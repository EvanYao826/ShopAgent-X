import pymysql

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'shop_agent_db',
    'charset': 'utf8mb4'
}

def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # Get all products with their image_url
    cursor.execute("SELECT id, image_url FROM product WHERE image_url IS NOT NULL AND image_url != ''")
    products = cursor.fetchall()

    count = 0
    for product_id, image_url in products:
        # Insert as main image (image_type='main', sort_order=0)
        cursor.execute("""
            INSERT INTO product_image (product_id, image_url, image_type, sort_order)
            VALUES (%s, %s, 'main', 0)
        """, (product_id, image_url))
        count += 1

    conn.commit()
    print(f"[OK] Inserted {count} product images into product_image table")

    # Verify
    cursor.execute("SELECT COUNT(*) FROM product_image")
    total = cursor.fetchone()[0]
    print(f"[OK] Total product_image records: {total}")

    # Show sample
    cursor.execute("""
        SELECT pi.product_id, p.title, pi.image_url, pi.image_type
        FROM product_image pi
        JOIN product p ON pi.product_id = p.id
        LIMIT 5
    """)
    for row in cursor.fetchall():
        print(f"  Product #{row[0]}: {row[1][:25]}... | {row[2]} | type={row[3]}")

    conn.close()

if __name__ == '__main__':
    main()
