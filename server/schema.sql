-- ============================================================
-- Schema para Sistema Cliente/Servidor
-- Base de datos: cliente_servidor_db
-- ============================================================

CREATE DATABASE IF NOT EXISTS cliente_servidor_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE cliente_servidor_db;

-- Tabla base de usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(100) NOT NULL UNIQUE,
    tipo_usuario VARCHAR(50)  NOT NULL
);

-- Subclase usuario normal
CREATE TABLE IF NOT EXISTS usuarios_normales (
    id BIGINT PRIMARY KEY,
    FOREIGN KEY (id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Tabla base de documentos (herencia JOINED)
CREATE TABLE IF NOT EXISTS documentos (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo                 VARCHAR(20)  NOT NULL,   -- discriminador: MENSAJE | ARCHIVO
    hash                 VARCHAR(64),
    contenido            LONGBLOB,                -- binario directo, sin base64
    contenido_encriptado LONGBLOB,                -- binario encriptado
    propietario_tipo     VARCHAR(20),             -- LOCAL | EXTERNO
    propietario_ip       VARCHAR(45),
    usuario_id           BIGINT,
    fecha_hora           DATETIME,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Subclase archivo
CREATE TABLE IF NOT EXISTS archivos (
    id            BIGINT PRIMARY KEY,
    nombre        VARCHAR(255),
    extension     VARCHAR(20),
    tamanio       BIGINT,
    ruta_original VARCHAR(1024),
    FOREIGN KEY (id) REFERENCES documentos(id) ON DELETE CASCADE
);

-- Subclase mensaje
CREATE TABLE IF NOT EXISTS mensajes (
    id     BIGINT PRIMARY KEY,
    asunto VARCHAR(255),
    FOREIGN KEY (id) REFERENCES documentos(id) ON DELETE CASCADE
);

-- Imágenes de rostro para identificación de emociones
CREATE TABLE IF NOT EXISTS imagenes_rostro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL,
    extension       VARCHAR(20)  NOT NULL,
    tamanio         BIGINT       NOT NULL,
    documento_id    BIGINT       NOT NULL,
    fecha_hora      DATETIME     NOT NULL,
    emocion         VARCHAR(50),
    FOREIGN KEY (documento_id) REFERENCES documentos(id) ON DELETE CASCADE
);

-- Logs
CREATE TABLE IF NOT EXISTS logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT,
    Tipo       VARCHAR(20),
    mensaje    TEXT,
    fecha_hora DATETIME,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
