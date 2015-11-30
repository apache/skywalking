create TABLE t_order (
  ORDER_ID varchar(32) not null,
  RESOURCE_ID int(11),
  PHONE_NUMBER varchar(11) not null,
  PACKAGE_ID int(11),
  MAIL_ACCOUNT varchar(45),
  PRIMARY KEY (ORDER_ID)
);
CREATE UNIQUE INDEX T_ORDER_ORDER_ID_uindex ON t_order (ORDER_ID ASC);