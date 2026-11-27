CREATE TABLE IF NOT EXISTS stations (
  code VARCHAR(8) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  city VARCHAR(60) NOT NULL
);

CREATE TABLE IF NOT EXISTS trains (
  id INT AUTO_INCREMENT PRIMARY KEY,
  number VARCHAR(10) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  source VARCHAR(8) NOT NULL,
  destination VARCHAR(8) NOT NULL,
  departs TIME NOT NULL,
  duration_minutes INT NOT NULL,
  run_days VARCHAR(7) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_train_source FOREIGN KEY (source) REFERENCES stations(code),
  CONSTRAINT fk_train_destination FOREIGN KEY (destination) REFERENCES stations(code)
);

CREATE TABLE IF NOT EXISTS train_classes (
  train_id INT NOT NULL,
  class_code VARCHAR(4) NOT NULL,
  seats INT NOT NULL,
  fare DECIMAL(10, 2) NOT NULL,
  PRIMARY KEY (train_id, class_code),
  CONSTRAINT fk_class_train FOREIGN KEY (train_id) REFERENCES trains(id)
);

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(10) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id INT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS bookings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  pnr CHAR(10) NOT NULL UNIQUE,
  user_id INT NOT NULL,
  train_id INT NOT NULL,
  journey_date DATE NOT NULL,
  class_code VARCHAR(4) NOT NULL,
  status VARCHAR(12) NOT NULL,
  fare_total DECIMAL(10, 2) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  cancelled_at TIMESTAMP NULL,
  CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_booking_train FOREIGN KEY (train_id) REFERENCES trains(id)
);

CREATE TABLE IF NOT EXISTS passengers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  booking_id INT NOT NULL,
  name VARCHAR(80) NOT NULL,
  age INT NOT NULL,
  gender CHAR(1) NOT NULL,
  seat_no INT NULL,
  CONSTRAINT fk_passenger_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);
