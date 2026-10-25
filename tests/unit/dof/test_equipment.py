import os,shutil,subprocess,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
class EquipmentPolicy(unittest.TestCase):
    def test_policy(self):
        node=os.environ.get('DOF_NODE_EXE') or shutil.which('node')
        if not node:self.skipTest('Node required')
        script=ROOT/'tests/unit/dof/equipment-policy.cjs'
        if node.endswith('.exe'):
            script=subprocess.check_output(['wslpath','-w',str(script)],text=True).strip()
        subprocess.run([node,str(script)],check=True)
