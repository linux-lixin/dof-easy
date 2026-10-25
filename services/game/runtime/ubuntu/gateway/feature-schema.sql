CREATE TABLE `randombox` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `item_num` int(11) NOT NULL,
  `Probability` int(11) NOT NULL,
  `Announcement` int(11) NOT NULL,
  `Grouping` int(11) NOT NULL,
  `rarity` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
CREATE TABLE `randombox_config` (
  `id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `item_key` int(11) NOT NULL,
  `item_keynum` int(11) NOT NULL,
  `Lucky_points` int(11) NOT NULL,
  `edxof` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
CREATE TABLE `randombox_user` (
  `uid` int(11) NOT NULL,
  `Lucky_points` int(11) DEFAULT NULL,
  PRIMARY KEY (`uid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
