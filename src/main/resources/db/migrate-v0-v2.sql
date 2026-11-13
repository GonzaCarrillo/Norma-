CREATE TABLE new_sessions (
    id TEXT NOT NULL PRIMARY KEY COLLATE BINARY CHECK(id='DEFAULT' OR (length(id) BETWEEN 8 AND 80 AND id NOT GLOB '*[^a-zA-Z0-9_-]*')),
    state TEXT NOT NULL DEFAULT 'IDLE' CHECK(state IN ('IDLE','AWAITING_DETAILS','AWAITING_CONFIRMATION')),
    pending TEXT,
    temp_image TEXT,
    snapshot TEXT,
    next_number INTEGER NOT NULL DEFAULT 9 CHECK(typeof(next_number)='integer' AND next_number BETWEEN 9 AND 2147483647),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);;


CREATE TABLE new_products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    piece_number INTEGER NOT NULL CHECK(typeof(piece_number)='integer' AND piece_number BETWEEN 1 AND 2147483646),
    title TEXT NOT NULL CHECK(length(trim(title))>0),
    price INTEGER NOT NULL CHECK(typeof(price)='integer' AND price BETWEEN 1 AND 2147483647),
    image_url TEXT NOT NULL CHECK(length(trim(image_url))>0),
    sold INTEGER NOT NULL DEFAULT 0 CHECK(typeof(sold)='integer' AND sold IN (0,1)),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(session_id, piece_number)
);;

INSERT INTO new_sessions(id, state, pending, temp_image, snapshot, next_number, updated_at)
SELECT s.id,
       s.state,
       s.pending,
       s.temp_image,
       s.snapshot,
       MAX(9, s.next_number, COALESCE((SELECT MAX(p.piece_number) + 1 FROM products p WHERE p.session_id = s.id), 9)),
       CURRENT_TIMESTAMP
FROM sessions s;;

INSERT INTO new_products(id, session_id, piece_number, title, price, image_url, sold, updated_at)
SELECT id, session_id, piece_number, title, price, image_url, sold, CURRENT_TIMESTAMP
FROM products;;

DROP TABLE products;;
DROP TABLE sessions;;

ALTER TABLE new_sessions RENAME TO sessions;;
ALTER TABLE new_products RENAME TO products;;
