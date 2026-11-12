"""Record archive checksums and config identity after pull-by-digest on CI."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    args = parser.parse_args()
    directory = Path(args.directory)
    images = {}
    for role in ('backend', 'frontend'):
        archive = directory / f'{role}.tar'
        image = os.environ[f'{role.upper()}_REF']
        descriptor = json.loads((directory / f'{role}-descriptor.json').read_text(encoding='utf-8'))
        for _ in range(3):
            if 'config' in descriptor:
                break
            platform = next(item for item in descriptor['manifests']
                            if item.get('platform', {}).get('os') == 'linux'
                            and item.get('platform', {}).get('architecture') == 'amd64')
            if not re.fullmatch(r'sha256:[a-f0-9]{64}', platform['digest']):
                raise RuntimeError('Invalid platform descriptor')
            child_ref = image.split('@')[0] + '@' + platform['digest']
            raw = subprocess.run(['docker', 'buildx', 'imagetools', 'inspect', child_ref, '--raw'],
                                 check=True, capture_output=True, text=True).stdout
            descriptor = json.loads(raw)
        config = descriptor['config']['digest']
        if not re.fullmatch(r'sha256:[a-f0-9]{64}', config):
            raise RuntimeError('Invalid image config digest')
        # Prove that the exported archive actually contains the exact config
        # inspected from the freshly pulled CI artifact, not only a tag name.
        with tarfile.open(archive) as saved:
            manifest = json.load(saved.extractfile('manifest.json'))
            if len(manifest) != 1:
                raise RuntimeError('Expected one application image per archive')
            expected_tag = image.split('@')[0] + ':sha-' + os.environ['GITHUB_SHA']
            if expected_tag not in (manifest[0]['RepoTags'] or []):
                raise RuntimeError('Archive tag does not match source SHA')
            raw_config = saved.extractfile(manifest[0]['Config']).read()
            if 'sha256:' + hashlib.sha256(raw_config).hexdigest() != config:
                raise RuntimeError('Exported archive config differs from pulled image')
        with archive.open('rb') as stream:
            checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
        images[role] = {
            'image': image, 'config_digest': config,
            'archive': archive.name, 'archive_sha256': checksum,
        }
    document = {'source_sha': os.environ['GITHUB_SHA'], 'images': images}
    (directory / 'image-provenance.json').write_text(json.dumps(document, indent=2) + '\n', encoding='utf-8')
    print('PASS: exported image archives match the pulled config digests; SHA256 recorded')


if __name__ == '__main__':
    main()
