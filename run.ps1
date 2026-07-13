# .env dosyasını oku ve ortam değişkenlerini yükle
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "env:$($name.Trim())" -Value $value.Trim()
}

Write-Host "✅ .env yuklendi" -ForegroundColor Green

# Uygulamayı başlat
mvn spring-boot:run