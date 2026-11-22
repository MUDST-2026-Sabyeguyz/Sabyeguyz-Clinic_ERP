"""Real HTTP/auth/patient persistence smoke. Use synthetic data only."""
import argparse
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request


def request(base, method, path, body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base.rstrip('/') + path, data, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected {expected}, got {status}')
    return json.loads(raw) if raw else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--verify-existing', help='Read back a previously recorded synthetic patient')
    args = parser.parse_args()
    username, password = os.environ['SMOKE_USERNAME'], os.environ['SMOKE_PASSWORD']
    base = args.base_url.rstrip('/')
    # Prove auth enforcement independently of the successful login.
    request(base, 'GET', '/api/patients', expected=401)
    login = request(base, 'POST', '/api/auth/login', {'username': username, 'password': password})
    token = login['token']
    if not token or login['role'] != 'MANAGER':
        raise RuntimeError('Expected a real Manager login')
    if args.verify_existing:
        original = json.loads(Path(args.verify_existing).read_text(encoding='utf-8'))
        patient_id, hn = original['patient_id'], original['hn']
    else:
        suffix = secrets.token_hex(6)
        payload = {
            'branchCode': 'RP', 'username': 'ci-' + suffix,
            'email': 'ci-' + suffix + '@example.invalid', 'password': secrets.token_hex(16),
            'title': 'นาย', 'fullName': 'ทดสอบ ระบบเดโม', 'birthDate': '1990-01-01',
            'gender': 'ชาย', 'phone': '0800000000', 'identityDocumentType': 'PASSPORT',
            'identityNumber': 'CI' + suffix, 'address': 'Synthetic test data',
            'drugAllergy': False, 'foodAllergy': False, 'chronicDisease': '',
            'emergencyContactName': 'Synthetic Contact', 'emergencyContactRelationship': 'Test',
            'emergencyContactPhone': '0800000001', 'privacyConsent': True,
        }
        patient = request(base, 'POST', '/api/patients', payload, token, expected=201)
        patient_id, hn = patient['id'], patient['hn']
    profile = request(base, 'GET', f'/api/patients/{patient_id}', token=token)
    if profile['id'] != patient_id or profile['hn'] != hn:
        raise RuntimeError('Patient readback did not match the original identity')
    results = request(base, 'GET', '/api/patients?query=' + urllib.parse.quote(hn), token=token)
    # Check the actual search contract rather than only HTTP 200.
    if not any(item['id'] == patient_id for item in results['items']):
        raise RuntimeError('Created patient was missing from search')
    evidence = {
        'status': 'PASS', 'base_url': base, 'source_sha': os.environ.get('SOURCE_SHA', 'local'),
        'patient_id': patient_id, 'hn': hn, 'verified_at_epoch': int(time.time()),
        'checks': ['unauthenticated_401', 'manager_login', 'profile_readback', 'search_match'],
        'mode': 'persistence_readback' if args.verify_existing else 'create_and_read',
    }
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: real login, authorization, patient profile and search; evidence saved')


if __name__ == '__main__':
    main()
