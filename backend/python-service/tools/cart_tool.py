import os
import requests
from typing import Dict, Any
from tools.base import Tool, ToolSchema, SchemaProperty, ToolMetadata
import logging

logger = logging.getLogger(__name__)

# Java 后端地址
JAVA_API_URL = os.getenv("JAVA_API_URL", "http://localhost:8080")


class CartTool(Tool):
    """购物车操作工具，通过 HTTP 调用 Java Cart API"""

    def __init__(self):
        input_schema = ToolSchema(
            properties={
                "action": SchemaProperty(
                    type="string",
                    description="操作类型：add/list/remove/update/clear/count",
                    required=True
                ),
                "user_id": SchemaProperty(
                    type="string",
                    description="用户ID",
                    required=True
                ),
                "product_id": SchemaProperty(
                    type="number",
                    description="商品ID（add/remove/update 必填）",
                    required=False
                ),
                "quantity": SchemaProperty(
                    type="number",
                    description="数量（add 默认1，update 必填，remove 可选）",
                    required=False
                ),
                "jwt_token": SchemaProperty(
                    type="string",
                    description="JWT 认证令牌",
                    required=False
                ),
            },
            type="object"
        )

        output_schema = ToolSchema(
            properties={
                "success": SchemaProperty(type="boolean", description="是否成功", required=True),
                "data": SchemaProperty(type="object", description="返回数据", required=False),
                "message": SchemaProperty(type="string", description="提示信息", required=False),
            },
            type="object"
        )

        metadata = ToolMetadata(
            timeout_ms=10000,
            max_retries=1,
            permission="user",
            description="购物车操作：加入/查看/删除/修改数量"
        )

        super().__init__(
            name="cart_operation",
            description="购物车操作：加入/查看/删除/修改数量",
            input_schema=input_schema,
            output_schema=output_schema,
            metadata=metadata
        )

    def _get_headers(self, jwt_token: str = None) -> dict:
        """构建请求头"""
        headers = {"Content-Type": "application/json"}
        if jwt_token:
            headers["Authorization"] = f"Bearer {jwt_token}"
        return headers

    def execute(self, parameters: Dict[str, Any]) -> Dict[str, Any]:
        action = parameters.get("action")
        user_id = parameters.get("user_id")

        if not user_id:
            return {"success": False, "message": "缺少 user_id"}

        handler = {
            "add": self._add,
            "list": self._list,
            "remove": self._remove,
            "update": self._update,
            "clear": self._clear,
            "count": self._count,
        }.get(action)

        if not handler:
            return {"success": False, "message": f"不支持的操作：{action}"}

        try:
            return handler(parameters)
        except requests.RequestException as e:
            logger.error(f"[CartTool] {action} HTTP error: {e}")
            return {"success": False, "message": f"网络请求失败：{str(e)}"}
        except Exception as e:
            logger.error(f"[CartTool] {action} failed: {e}", exc_info=True)
            return {"success": False, "message": f"操作失败：{str(e)}"}

    def _add(self, params: Dict) -> Dict:
        product_id = params.get("product_id")
        quantity = params.get("quantity", 1)
        jwt_token = params.get("jwt_token")

        if not product_id:
            return {"success": False, "message": "缺少 product_id"}

        resp = requests.post(
            f"{JAVA_API_URL}/api/cart/add",
            params={"productId": int(product_id)},
            headers=self._get_headers(jwt_token),
            timeout=5
        )

        if resp.status_code == 200:
            data = resp.json()
            if data.get("code") == 200:
                return {"success": True, "message": f"已将商品加入购物车", "data": {"product_id": product_id}}
            return {"success": False, "message": data.get("message", "添加失败")}

        logger.error(f"[CartTool] _add failed: HTTP {resp.status_code}, body={resp.text}")
        return {"success": False, "message": f"添加失败（HTTP {resp.status_code}）"}

    def _list(self, params: Dict) -> Dict:
        jwt_token = params.get("jwt_token")

        resp = requests.get(
            f"{JAVA_API_URL}/api/cart/list",
            headers=self._get_headers(jwt_token),
            timeout=5
        )

        if resp.status_code == 401 or resp.status_code == 403:
            return {"success": False, "message": "登录已过期，请重新登录"}
        if resp.status_code != 200:
            return {"success": False, "message": f"查询失败（HTTP {resp.status_code}）"}

        data = resp.json()
        if data.get("code") != 200:
            return {"success": False, "message": data.get("message", "查询失败")}

        raw_items = data.get("data", [])
        if not raw_items:
            return {"success": True, "message": "购物车是空的", "data": {"items": [], "count": 0}}

        # 将 Java CartItemVO 转换为 ShoppingAgent 期望的扁平格式
        cart_items = []
        for idx, item in enumerate(raw_items, start=1):
            product = item.get("product") or {}
            cart_items.append({
                "index": idx,
                "id": item.get("id"),
                "product_id": item.get("productId"),
                "title": product.get("title", ""),
                "brand": product.get("brand", ""),
                "price": float(product.get("basePrice", 0)) if product.get("basePrice") else 0,
                "image_url": product.get("imageUrl", ""),
                "quantity": item.get("quantity", 1),
            })

        return {
            "success": True,
            "data": {"items": cart_items, "count": len(cart_items)},
            "message": f"购物车中有 {len(cart_items)} 件商品"
        }

    def _remove(self, params: Dict) -> Dict:
        product_id = params.get("product_id")
        cart_item_id = params.get("cart_item_id")
        quantity = params.get("quantity")
        jwt_token = params.get("jwt_token")

        # 按购物车项ID删除（精确删除单条记录，用于"删除第N个"场景）
        if cart_item_id:
            logger.info(f"[CartTool] _remove by cart_item_id: {cart_item_id}")
            resp = requests.delete(
                f"{JAVA_API_URL}/api/cart/removeById",
                params={"cartItemId": int(cart_item_id)},
                headers=self._get_headers(jwt_token),
                timeout=5
            )
            if resp.status_code == 200:
                data = resp.json()
                if data.get("code") == 200:
                    return {"success": True, "message": "已将商品从购物车移除"}
                return {"success": False, "message": data.get("message", "删除失败")}
            logger.error(f"[CartTool] _remove failed: HTTP {resp.status_code}, body={resp.text}")
            return {"success": False, "message": f"删除失败（HTTP {resp.status_code}）"}

        if not product_id:
            return {"success": False, "message": "缺少 product_id"}

        logger.info(f"[CartTool] _remove: product_id={product_id}, quantity={quantity}")

        if quantity is not None and quantity > 0:
            # 按数量删除：调用 remove 接口带 quantity 参数
            resp = requests.delete(
                f"{JAVA_API_URL}/api/cart/remove",
                params={"productId": int(product_id), "quantity": int(quantity)},
                headers=self._get_headers(jwt_token),
                timeout=5
            )
        else:
            # 全部删除
            resp = requests.delete(
                f"{JAVA_API_URL}/api/cart/remove",
                params={"productId": int(product_id)},
                headers=self._get_headers(jwt_token),
                timeout=5
            )

        if resp.status_code == 200:
            data = resp.json()
            if data.get("code") == 200:
                return {"success": True, "message": "已将商品从购物车移除"}
            return {"success": False, "message": data.get("message", "删除失败")}

        logger.error(f"[CartTool] _remove failed: HTTP {resp.status_code}, body={resp.text}")
        return {"success": False, "message": f"删除失败（HTTP {resp.status_code}）"}

    def _update(self, params: Dict) -> Dict:
        product_id = params.get("product_id")
        quantity = params.get("quantity")
        jwt_token = params.get("jwt_token")

        if not product_id:
            return {"success": False, "message": "缺少 product_id"}
        if not quantity or quantity < 1:
            return {"success": False, "message": "数量必须大于0"}

        resp = requests.put(
            f"{JAVA_API_URL}/api/cart/update",
            params={"productId": int(product_id), "quantity": int(quantity)},
            headers=self._get_headers(jwt_token),
            timeout=5
        )

        if resp.status_code == 200:
            data = resp.json()
            if data.get("code") == 200:
                return {"success": True, "message": f"已更新数量为 {quantity}"}
            return {"success": False, "message": data.get("message", "更新失败")}

        logger.error(f"[CartTool] _update failed: HTTP {resp.status_code}, body={resp.text}")
        return {"success": False, "message": f"更新失败（HTTP {resp.status_code}）"}

    def _clear(self, params: Dict) -> Dict:
        """清空购物车：先获取列表，逐个删除"""
        jwt_token = params.get("jwt_token")

        # 先获取购物车列表
        list_result = self._list(params)
        if not list_result.get("success"):
            return list_result

        items = list_result.get("data", {}).get("items", [])
        if not items:
            return {"success": True, "message": "购物车已经是空的了"}

        success_count = 0
        for item in items:
            resp = requests.delete(
                f"{JAVA_API_URL}/api/cart/remove",
                params={"productId": item["product_id"]},
                headers=self._get_headers(jwt_token),
                timeout=5
            )
            if resp.status_code == 200:
                data = resp.json()
                if data.get("code") == 200:
                    success_count += 1

        if success_count > 0:
            return {"success": True, "message": f"已清空购物车，共移除 {success_count} 件商品"}
        return {"success": False, "message": "清空购物车失败"}

    def _count(self, params: Dict) -> Dict:
        jwt_token = params.get("jwt_token")

        resp = requests.get(
            f"{JAVA_API_URL}/api/cart/count",
            headers=self._get_headers(jwt_token),
            timeout=5
        )

        if resp.status_code == 200:
            data = resp.json()
            if data.get("code") == 200:
                return {"success": True, "data": {"count": data.get("data", 0)}}
            return {"success": False, "message": data.get("message", "查询失败")}

        return {"success": False, "message": f"查询失败（HTTP {resp.status_code}）"}
