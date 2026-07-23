--liquibase formatted sql

--changeset vdmytriv:04-seed-roles
INSERT INTO roles (name)
VALUES ('CUSTOMER'),
       ('MANAGER');
