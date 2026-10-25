import unittest
from scripts.dof.migrate_local import convert_sql

class SQLMigration(unittest.TestCase):
    def test_identifiers_change_but_business_values_passwords_and_comments_do_not(self):
        source="-- qk source\nCREATE DATABASE `qk`; USE qk; CREATE TABLE `qingkong_config` (`id` int); INSERT INTO `qk`.`qingkong_config` VALUES (1,'qk','qingkong_config','p\\\'ass'); GRANT SELECT ON `qk`.* TO 'game'@'localhost' IDENTIFIED BY 'qk';"
        converted,changes=convert_sql(source)
        self.assertIn('CREATE DATABASE `dof_native`; USE dof_native;',converted)
        self.assertIn("VALUES (1,'qk','qingkong_config','p\\\'ass')",converted)
        self.assertIn("IDENTIFIED BY 'qk'",converted)
        self.assertTrue(converted.startswith('-- qk source\n'))
        self.assertEqual(changes,{'qk':4,'qingkong_config':2})

    def test_versioned_sql_and_robot_tables(self):
        result,changes=convert_sql('/*!50001 CREATE VIEW `qk`.`qk_ai` AS SELECT * FROM `qk_ai_user` */;')
        self.assertEqual(result,'/*!50001 CREATE VIEW `dof_native`.`ai` AS SELECT * FROM `ai_user` */;')
        self.assertEqual(changes['qk_ai'],1)

    def test_ambiguous_quotes_and_truncated_values_fail_closed(self):
        for sql in ["INSERT INTO qk.t VALUES ('missing",'SET SQL_MODE="ANSI_QUOTES";',"SELECT `missing"]:
            with self.assertRaises(ValueError):convert_sql(sql)
