"""Import only explicit release images into provisioned Docker Desktop kind nodes."""
import argparse, json, re, subprocess, tempfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--nodes", required=True)
p.add_argument("images", nargs="+")
a=p.parse_args()
nodes=json.loads(Path(a.nodes).read_text(encoding="utf-8-sig"))
if not isinstance(nodes,list) or not nodes or any(not re.fullmatch(r"desktop-[a-z0-9-]+",n) for n in nodes):
    raise SystemExit("Invalid provisioned Docker Desktop node list")
for image in a.images:
    if not re.fullmatch(r"(?:clinic-actions-(?:backend|frontend):[a-f0-9]{12}-[0-9]+-[0-9]+|postgres:16-alpine)",image):
        raise SystemExit("Only this release's image names are allowed")
    with tempfile.TemporaryDirectory(prefix="clinic-actions-image-") as temp:
        archive=Path(temp)/"image.tar"
        subprocess.run(["docker","image","save","--platform","linux/amd64","--output",str(archive),image],check=True)
        for node in nodes:
            subprocess.run(["docker","inspect",node],check=True,stdout=subprocess.DEVNULL)
            with archive.open("rb") as stream:
                subprocess.run(["docker","exec","-i",node,"ctr","--namespace","k8s.io","images","import","--all-platforms","-"],stdin=stream,check=True)
