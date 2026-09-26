# Xiaomi (MIUI & HyperOS) Radyo Uygulaması Kurulum ve RAM Koruması Rehberi

Bu proje, Xiaomi telefonlar için özel olarak optimize edilmiş kesintisiz canlı radyo oynatıcısıdır.

## 1. Uygulamanın Özellikleri
- **Varsayılan Kanallar:** Kral FM (92.0 MHz), Süper FM (90.8 MHz), Alem FM (89.2 MHz).
- **Kanal Yönetimi:** Ayarlar sekmesinden yeni kanal ekleme ve istenmeyen kanalları tek dokunuşla silme.
- **İnternet Bekleme Sayacı:** İnternet gidip geldiğinde belirlenen süre (kutuya girilen saniye) ana ekranda geriye sayar ve süre bitince son dinlenen kanalı otomatik çalmaya devam eder.
- **Buffer Süresi Girişi:** Kutuya girilen buffer süresine (sn) göre ön bellek oluşturur.
- **Bildirim Çubuğu Kontrolleri:** Bildirimde Kanal Adı, Play/Pause ve Next tuşları yer alır. Pause yapıldığında bildirim tamamen temizlenir.
- **4x1 Ana Ekran Widget'ı:** Büyük ve arası açık, dokunması çok kolay Play/Pause ve Next tuşlarıyla ana ekrandan kontrol edilir.

## 2. Xiaomi RAM Temizleme Koruması (Uygulama Asla Kapanmasın)
Xiaomi (MIUI / HyperOS) arka planda çalışan müzik ve radyo uygulamalarını RAM temizliği yaparken sonlandırabilir. Bunu önlemek için şu 3 basit adımı uygulayın:

### Adım 1: Son Uygulamalar Ekranında Kilitleme
1. Uygulamayı açın ve ardından ekranın altından yukarı kaydırarak **Son Uygulamalar** (Recents) ekranına gelin.
2. Radyo uygulaması kartının üzerine **basılı tutun**.
3. Açılan yan menüdeki **Kilit (Asma Kilit 🔒)** simgesine dokunun.
*(Artık RAM temizle "X" tuşuna bassanız bile uygulama kapatılmaz).*

### Adım 2: Otomatik Başlatma (Autostart) İzni
1. Telefonunuzun **Ayarlar** bölümüne gidin.
2. **Uygulamalar > İzinler > Otomatik Başlatma** yolunu izleyin.
3. **Xiaomi Canlı Radyo** uygulamasını bulun ve anahtarı **AÇIK** duruma getirin.

### Adım 3: Pil Tasarrufu Kısıtlamasını Kaldırma
1. **Ayarlar > Uygulamalar > Uygulamaları Yönet > Xiaomi Canlı Radyo** yoluna girin.
2. **Pil Tasarrufu** seçeneğine dokunun.
3. **"Kısıtlama Yok" (No restrictions)** seçeneğini işaretleyin.

## 3. Flutter Projesini Çalıştırma
```bash
# Bağımlılıkları yükleyin
flutter pub get

# Xiaomi telefonunuzu USB ile bağlayın ve çalıştırın
flutter run
```
