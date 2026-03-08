create table if not exists owners (
  id varchar(32) primary key,
  first_name varchar(128) not null,
  last_name varchar(128) not null
);

create table if not exists pets (
  id bigint primary key,
  owner_id varchar(32) not null,
  name varchar(128) not null,
  type varchar(64) not null,
  birth_date varchar(32) not null,
  constraint fk_pets_owner foreign key (owner_id) references owners(id)
);
