import copy
import csv
import importlib.util
import io
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('admin_inventory', ROOT / 'scripts/dof/admin_inventory.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class InventoryTests(unittest.TestCase):
    def fixtures(self):
        scope = dict(discoveredClasses=1, discoveredOperations=1, nativeMenuAuditComplete=False,
                     groups=[dict(id='feature-001', title='test', operations=[dict(id='feature-001-01', name='test', included=True,
                                  nativeMenuConfirmed=False, managementImplementation=dict(state='configuration_implemented'))])])
        plan = dict(packages=[dict(id='wp01', groupIds=['feature-001'], dependsOn=[], state='in_progress', completionEvidence=[])])
        return scope, plan

    def report(self, scope, plan):
        return module.inventory(scope, plan, minimum_classes=1, minimum_operations=1)

    def test_implemented_labels_and_missing_stage_evidence_never_count_as_completion(self):
        scope, plan = self.fixtures()
        report = self.report(scope, plan)
        self.assertFalse(report['completeReplacementEligible'])
        self.assertEqual(report['completedOperations'], 0)
        op = scope['groups'][0]['operations'][0]
        op['verification'] = {'implementation': {'state': 'passed', 'evidence': []}}
        with self.assertRaisesRegex(ValueError, 'no evidence'):
            self.report(scope, plan)
        op['verification']['implementation']['evidence'] = ['tests/implementation-contract.json']
        op['status'] = 'complete'
        with self.assertRaisesRegex(ValueError, 'lacks stage evidence'):
            self.report(scope, plan)

    def test_group_coverage_and_dependency_cycles_are_rejected(self):
        scope, plan = self.fixtures()
        plan['packages'].append(copy.deepcopy(plan['packages'][0]))
        plan['packages'][-1]['id'] = 'wp02'
        with self.assertRaisesRegex(ValueError, 'more than once'):
            self.report(scope, plan)
        plan['packages'].pop()
        plan['packages'][0]['dependsOn'] = ['wp01']
        with self.assertRaisesRegex(ValueError, 'Cyclic'):
            self.report(scope, plan)

    def test_spreadsheet_export_cannot_execute_operator_authored_formula_cells(self):
        scope, plan = self.fixtures()
        scope['groups'][0]['operations'][0]['name'] = '=HYPERLINK("https://example.invalid")'
        rows = list(csv.DictReader(io.StringIO(module.csv_report(self.report(scope, plan)))))
        self.assertTrue(rows[0]['name'].startswith("'="))
        self.assertEqual(rows[0]['implementation'], 'pending')

    def test_real_scope_has_exactly_one_owner_per_operation_and_no_accepted_replacement(self):
        resources = ROOT / 'services/admin/backend/src/main/resources/dof'
        report = module.inventory(json.loads((resources / 'scope.json').read_text()), json.loads((resources / 'execution-plan.json').read_text()))
        self.assertEqual(report['groups'], 71)
        self.assertGreaterEqual(report['operations'], 239)
        self.assertFalse(report['completeReplacementEligible'])


if __name__ == '__main__':
    unittest.main()
