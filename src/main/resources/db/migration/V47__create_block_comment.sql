CREATE TABLE block_comment (
    id INT NOT NULL AUTO_INCREMENT,
    block_id INT NOT NULL,
    author_id INT,
    author_name VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (block_id) REFERENCES `block`(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES `user`(id) ON DELETE SET NULL
);

CREATE INDEX idx_block_comment_block_id ON block_comment (block_id);
