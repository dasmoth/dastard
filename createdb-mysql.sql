create table dastard_user (
        id            integer primary key not null auto_increment,
        username      varchar(40) not null,
        email         varchar(40),
        secr          varchar(255)
);

insert into dastard_user (username) values ('Public');

create table das_reference(
        id            integer primary key not null auto_increment,
        name          varchar(40),
        species       int not null,
        map_master    varchar(255)
);

insert into das_reference (name, species) values ('Human NCBI36', '9303');
insert into das_reference (name, species) values ('Human GRCv37', '9303');

create table das_source_meta_cache (
        source            integer not null,
        min_score         double,
        max_score         double,
        longest_feature   int
);

create table das_source(
        id            integer primary key not null auto_increment,
        owner         integer not null,
        name          varchar(40) not null,
        reference     int not null,
        description   varchar(255),

        unique(name)
);

create table das_source_property(
        das_source    integer not null,
        name          varchar(40),
        value         varchar(255)
);

create table feature_type (
       id             integer primary key not null auto_increment,
       das_source     integer not null,
       type           varchar(40) not null,
       source         varchar(40) not null
);

create table feature(
        id            integer primary key not null auto_increment,
        das_source    integer not null,
        seq_name      varchar(40) not null,
        seq_min       integer not null,
        seq_max       integer not null,
        seq_strand    integer not null default 0,
	type          integer not null,
        score         double
);

create table feature_property(
        feature       integer not null,
        name          varchar(40),
        value         varchar(255)
);

create table type_style (
        type         integer not null,
        style        varchar(40) not null,
        color1       varchar(40) not null,
        color2       varchar(40) not null,
        color3       varchar(40) not null
);

create index feature_seq on feature (seq_name);
create index feature_pos on feature (seq_name, seq_min);
