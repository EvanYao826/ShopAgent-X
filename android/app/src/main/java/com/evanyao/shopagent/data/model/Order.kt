package com.evanyao.shopagent.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class Order(
    @SerializedName(value = "id", alternate = ["order_id"])
    val id: Long,
    @SerializedName(value = "orderNo", alternate = ["order_no"])
    val orderNo: String,
    val userId: Long? = null,
    val status: Int = 0,
    @SerializedName(value = "totalAmount", alternate = ["total_amount"])
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "payAmount", alternate = ["pay_amount"])
    val payAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "freightAmount", alternate = ["freight_amount"])
    val freightAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "receiverName", alternate = ["receiver_name"])
    val receiverName: String? = null,
    @SerializedName(value = "receiverPhone", alternate = ["receiver_phone"])
    val receiverPhone: String? = null,
    @SerializedName(value = "receiverAddress", alternate = ["receiver_address"])
    val receiverAddress: String? = null,
    val remark: String? = null,
    val items: List<OrderItem> = emptyList(),
    @SerializedName(value = "createTime", alternate = ["create_time"])
    val createTime: String? = null,
    @SerializedName(value = "payTime", alternate = ["pay_time"])
    val payTime: String? = null,
    @SerializedName(value = "deliveryTime", alternate = ["delivery_time"])
    val deliveryTime: String? = null,
    @SerializedName(value = "receiveTime", alternate = ["receive_time"])
    val receiveTime: String? = null,
    @SerializedName(value = "updateTime", alternate = ["update_time"])
    val updateTime: String? = null
) {
    companion object {
        const val STATUS_PENDING_PAY = 0      // 待付款
        const val STATUS_PENDING_DELIVERY = 1  // 待发货
        const val STATUS_PENDING_RECEIVE = 2   // 待收货
        const val STATUS_COMPLETED = 3         // 已完成
        const val STATUS_CANCELLED = 4         // 已取消

        fun getStatusText(status: Int): String = when (status) {
            STATUS_PENDING_PAY -> "待付款"
            STATUS_PENDING_DELIVERY -> "待发货"
            STATUS_PENDING_RECEIVE -> "待收货"
            STATUS_COMPLETED -> "已完成"
            STATUS_CANCELLED -> "已取消"
            else -> "未知状态"
        }
    }

    fun getStatusText(): String = getStatusText(status)
}

data class OrderItem(
    @SerializedName(value = "id", alternate = ["item_id"])
    val id: Long? = null,
    @SerializedName(value = "orderId", alternate = ["order_id"])
    val orderId: Long? = null,
    @SerializedName(value = "productId", alternate = ["product_id"])
    val productId: Long,
    @SerializedName(value = "productTitle", alternate = ["product_title"])
    val productTitle: String,
    @SerializedName(value = "productImage", alternate = ["product_image"])
    val productImage: String? = null,
    @SerializedName(value = "skuId", alternate = ["sku_id"])
    val skuId: Long? = null,
    @SerializedName(value = "skuProperties", alternate = ["sku_properties"])
    val skuProperties: String? = null,
    val price: BigDecimal,
    val quantity: Int = 1,
    @SerializedName(value = "totalAmount", alternate = ["total_amount"])
    val totalAmount: BigDecimal = BigDecimal.ZERO
)

data class CreateOrderRequest(
    @SerializedName(value = "addressId", alternate = ["address_id"])
    val addressId: Long,
    val items: List<CreateOrderItem>,
    val remark: String? = null,
    @SerializedName(value = "receiverName", alternate = ["receiver_name"])
    val receiverName: String? = null,
    @SerializedName(value = "receiverPhone", alternate = ["receiver_phone"])
    val receiverPhone: String? = null
)

data class CreateOrderItem(
    @SerializedName(value = "productId", alternate = ["product_id"])
    val productId: Long,
    @SerializedName(value = "skuId", alternate = ["sku_id"])
    val skuId: Long? = null,
    val quantity: Int = 1
)
