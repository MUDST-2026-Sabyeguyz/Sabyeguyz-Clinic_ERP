"""Stream a verified archive into the Minikube node's persistent /var mount."""
import argparse
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--container', required=True, choices=['clinic-phase1'])
parser.add_argument('--source', required=True)
parser.add_argument('--destination', required=True)
args = parser.parse_args()
if not re.fullmatch(r'/var/lib/clinic-demo-[a-f0-9]{32}\.tar', args.destination):
    raise RuntimeError('Unexpected demonstration import path')
with Path(args.source).open('rb') as archive:
    subprocess.run(['docker', 'exec', '-i', args.container, 'sh', '-c',
                    'cat > ' + args.destination], stdin=archive, check=True)
print('Verified image archive transferred to the selected Minikube node')
