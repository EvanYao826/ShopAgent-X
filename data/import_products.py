import os
import json
import pymysql
from pathlib import Path

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'shop_agent_db',
    'charset': 'utf8mb4'
}

DATASET_DIR = Path(__file__).parent / 'ecommerce_agent_dataset'

CATEGORY_MAPPING = {
    '1_美妆护肤': {'name': '美妆护肤', 'description': '面部护肤、彩妆、香水等美妆产品'},
    '2_数码电子': {'name': '数码电子', 'description': '手机、电脑、耳机等数码产品'},
    '3_服饰运动': {'name': '服饰运动', 'description': '服装、鞋靴、运动装备等'},
    '4_食品生活': {'name': '食品生活', 'description': '零食、饮料、生鲜等食品及生活用品'}
}

def connect_db():
    return pymysql.connect(**DB_CONFIG)

def get_category_id(cursor, category_name):
    cursor.execute("SELECT id FROM category WHERE name = %s", (category_name,))
    result = cursor.fetchone()
    if result:
        return result[0]
    return None

def insert_category(cursor, dir_name):
    info = CATEGORY_MAPPING.get(dir_name)
    if not info:
        return None
    cursor.execute("INSERT INTO category (name, description, sort_order) VALUES (%s, %s, %s)",
                   (info['name'], info['description'], int(dir_name.split('_')[0])))
    return cursor.lastrowid

def parse_product_json(json_path):
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    rag = data.get('rag_knowledge', {})
    reviews = rag.get('user_reviews', [])
    faqs = rag.get('official_faq', [])

    ratings = [r.get('rating', 5) for r in reviews]
    avg_rating = round(sum(ratings) / len(ratings), 2) if ratings else 5.0

    return {
        'product_code': data.get('product_id', os.path.splitext(os.path.basename(json_path))[0]),
        'title': data.get('title', ''),
        'brand': data.get('brand', ''),
        'sub_category': data.get('sub_category', ''),
        'base_price': float(data.get('base_price', 0)),
        'image_path': data.get('image_path', ''),
        'description': rag.get('marketing_description', ''),
        'tags': data.get('brand', ''),
        'rating': avg_rating,
        'review_count': len(reviews),
        'sales_count': 0,
        'sku_list': data.get('skus', []),
        'reviews': reviews,
        'faqs': faqs,
    }

def insert_product(cursor, category_id, product_data):
    cursor.execute("""
        INSERT INTO product (product_code, category_id, title, brand, sub_category,
                            base_price, image_url, description, tags, rating,
                            review_count, sales_count, status, embedding_status)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """, (
        product_data['product_code'],
        category_id,
        product_data['title'],
        product_data['brand'],
        product_data['sub_category'],
        product_data['base_price'],
        product_data['image_path'],
        product_data['description'],
        product_data['tags'],
        product_data['rating'],
        product_data['review_count'],
        product_data['sales_count'],
        1,
        0
    ))
    return cursor.lastrowid

def insert_sku(cursor, product_id, sku_data, index):
    sku_code = sku_data.get('sku_id', f"s_{product_id}_{index}")
    properties = json.dumps(sku_data.get('properties', {}), ensure_ascii=False)
    price = float(sku_data.get('price', 0))
    stock = int(sku_data.get('stock', 999))
    is_default = 1 if index == 0 else 0

    cursor.execute("""
        INSERT INTO product_sku (product_id, sku_code, properties, price, stock, is_default)
        VALUES (%s, %s, %s, %s, %s, %s)
    """, (product_id, sku_code, properties, price, stock, is_default))

def insert_reviews(cursor, product_id, reviews):
    for review in reviews:
        cursor.execute("""
            INSERT INTO product_review (product_id, nickname, rating, content, is_anonymous)
            VALUES (%s, %s, %s, %s, %s)
        """, (
            product_id,
            review.get('nickname', '匿名用户'),
            int(review.get('rating', 5)),
            review.get('content', ''),
            0
        ))

def insert_faqs(cursor, product_id, faqs):
    for i, faq in enumerate(faqs):
        cursor.execute("""
            INSERT INTO product_faq (product_id, question, answer, sort_order)
            VALUES (%s, %s, %s, %s)
        """, (product_id, faq.get('question', ''), faq.get('answer', ''), i))

def main():
    print("Starting product import...")

    conn = None
    try:
        conn = connect_db()
        cursor = conn.cursor()
        print("[OK] Database connected")

        total_products = 0
        total_skus = 0
        total_reviews = 0
        total_faqs = 0

        for category_dir in DATASET_DIR.iterdir():
            if not category_dir.is_dir():
                continue

            dir_name = category_dir.name
            if dir_name not in CATEGORY_MAPPING:
                continue

            category_id = get_category_id(cursor, CATEGORY_MAPPING[dir_name]['name'])
            if not category_id:
                category_id = insert_category(cursor, dir_name)
                print(f"[OK] Category: {CATEGORY_MAPPING[dir_name]['name']}")

            data_dir = category_dir / 'data'
            if not data_dir.exists():
                continue

            for json_file in sorted(data_dir.glob('*.json')):
                try:
                    product_data = parse_product_json(json_file)
                    product_id = insert_product(cursor, category_id, product_data)

                    for i, sku in enumerate(product_data.get('sku_list', [])):
                        insert_sku(cursor, product_id, sku, i)
                        total_skus += 1

                    reviews = product_data.get('reviews', [])
                    if reviews:
                        insert_reviews(cursor, product_id, reviews)
                        total_reviews += len(reviews)

                    faqs = product_data.get('faqs', [])
                    if faqs:
                        insert_faqs(cursor, product_id, faqs)
                        total_faqs += len(faqs)

                    total_products += 1
                    print(f"[{total_products:03d}] {product_data['title'][:30]} | RMB{product_data['base_price']} | rating={product_data['rating']} | {len(reviews)} reviews")

                except Exception as e:
                    print(f"[FAIL] {json_file}: {e}")

        conn.commit()
        print(f"\n{'='*50}")
        print(f"[DONE] Import complete!")
        print(f"  Products: {total_products}")
        print(f"  SKUs:     {total_skus}")
        print(f"  Reviews:  {total_reviews}")
        print(f"  FAQs:     {total_faqs}")

    except Exception as e:
        print(f"\n[FAIL] Database error: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn:
            conn.close()

if __name__ == '__main__':
    main()
