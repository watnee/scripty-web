CREATE TABLE audition (
    id int NOT NULL AUTO_INCREMENT,
    actor_id int NOT NULL,
    person_id int NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (actor_id, person_id),
    FOREIGN KEY (actor_id) REFERENCES actor(id) ON DELETE CASCADE,
    FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
);

CREATE INDEX idx_audition_person_id ON audition (person_id);
