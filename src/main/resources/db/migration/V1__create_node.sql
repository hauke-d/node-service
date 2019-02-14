create table node (
  id bigserial primary key,
  name text not null,
  parent_id bigint,
  root_id bigint,
  height int not null,
  constraint fk_parent_id foreign key (parent_id) references node(id)
);

create index ix_parent_id on node(parent_id);

insert into node (name, parent_id, height) values ('root', null, 0);
update node set root_id = (select id from node limit 1);

alter table node alter column root_id set not null;