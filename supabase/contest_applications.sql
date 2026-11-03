CREATE TABLE contest_applications (
    id SERIAL PRIMARY KEY,
    contest_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK(status IN ('pending', 'approved', 'rejected'))
);