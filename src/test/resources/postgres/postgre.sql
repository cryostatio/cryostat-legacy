SELECT 'CREATE DATABASE cryostat'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cryostat')\gexec

create table if not exists PluginInfo (
    id pg-uuid not null,
    callback varchar(255),
    realm varchar(255) not null,
    subtree jsonb not null,
    primary key (id)
);
