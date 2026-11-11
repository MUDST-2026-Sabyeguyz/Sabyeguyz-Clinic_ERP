param(
    [int]$Port = 80
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Sabyeguyz Web Sharing Tool             " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. แสดง IP สำหรับคนในวง Wi-Fi / LAN เดียวกัน
$wifiIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi*" -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notlike "169.*" }).IPAddress
if (-not $wifiIp) {
    $wifiIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.*" } | Select-Object -First 1).IPAddress
}

Write-Host "`n[1] สำาหรับคนในวง Wi-Fi เดียวกัน:" -ForegroundColor Yellow
if ($Port -eq 80) {
    Write-Host "   -> http://$wifiIp" -ForegroundColor Green
} else {
    Write-Host "   -> http://${wifiIp}:$Port" -ForegroundColor Green
}
Write-Host "   (หมายเหตุ: เครื่องอื่นต้องต่อ Wi-Fi เดียวกัน และ Windows Firewall ต้องอนุญาตพอร์ต $Port)" -ForegroundColor Gray

# 2. แชร์ผ่านอินเทอร์เน็ต (Tunnel) สำหรับคนอยู่นอกบ้าน/ต่อมือถือ 4G/5G
Write-Host "`n[2] สำาหรับเปิดผ่านอินเทอร์เน็ต (ทุกที่ในโลก):" -ForegroundColor Yellow
Write-Host "   กำาลังเริ่มสร้างลิงก์สาธารณะ (Tunnel) ไปที่พอร์ต $Port..." -ForegroundColor Cyan

if (Get-Command cloudflared -ErrorAction SilentlyContinue) {
    Write-Host "   พบ Cloudflare Tunnel! กำาลังเชื่อมต่อ..." -ForegroundColor Green
    cloudflared tunnel --url http://localhost:$Port
} else {
    Write-Host "   กำาลังใช้ localtunnel (ไม่ต้องติดตั้งโปรแกรมเพิ่ม)..." -ForegroundColor Green
    Write-Host "   เมื่อได้ URL (https://xxxx.loca.lt) สามารถส่งให้เพื่อนเปิดได้เลย" -ForegroundColor Yellow
    Write-Host "   (กด Ctrl+C เพื่อหยุดการแชร์)`n" -ForegroundColor Gray
    npx localtunnel --port $Port
}
