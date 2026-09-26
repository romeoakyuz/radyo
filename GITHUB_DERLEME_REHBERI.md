# GitHub Actions ile Ücretsiz & Otomatik APK Derleme Rehberi

Bilgisayarınıza hiçbir şey (Flutter, Android Studio, Java) kurmadan, GitHub'ın ücretsiz bulut sunucularını kullanarak **APK dosyanızı 3 dakikada** derleyebilirsiniz.

---

## 🚀 Adım Adım GitHub Derleme (Build):

### 1. Adım: GitHub Deposu (Repository) Oluşturun
1. [github.com](https://github.com) sitesine girip hesabınıza giriş yapın.
2. Sağ üstteki **"+"** ikonuna basıp **"New repository"** seçin.
3. Repository name kısmına örneğin **`xiaomi-canli-radyo`** yazın.
4. Gizliliği **Public** veya **Private** seçip **"Create repository"** butonuna tıklayın.

---

### 2. Adım: Proje Dosyalarını Yükleyin
1. Uygulamamızdaki **"Tüm Projeyi İndir (.ZIP)"** butonuna basarak ZIP arşivini indirin.
2. İndirdiğiniz ZIP dosyasını bilgisayarınızda bir klasöre çıkartın.
3. GitHub deponuzun ana sayfasında **"uploading an existing file"** linkine tıklayın.
4. Çıkarttığınız tüm dosya ve klasörleri (özellikle **`.github`**, **`lib`**, **`android`**, **`pubspec.yaml`**) sürükleyip bırakın.
5. Alttaki yeşil **"Commit changes"** butonuna basın.

---

### 3. Adım: APK Derlemesini Başlatın
1. Deponuzun üst menüsündeki **"Actions"** sekmesine tıklayın.
2. Sol tarafta **"Xiaomi Radyo APK Derleme"** iş akışını göreceksiniz.
3. Sağ taraftaki **"Run workflow"** butonuna tıklayın ve yeşil butonu onaylayın.
4. GitHub'ın bulut sunucuları otomatik olarak:
   - Java 17 ve Flutter'ı hazırlar,
   - Kral FM, Süper FM, Alem FM ve Xiaomi 4x1 Widget kodlarını derler,
   - Ortalama 2-3 dakika içinde **`app-release.apk`** dosyasını üretir!

---

### 4. Adım: APK Dosyasını İndirin ve Telefona Kurun
1. Biten göreve (yeşil onay işareti çıkınca) tıklayın.
2. Sayfanın en altındaki **"Artifacts"** (Çıktılar) bölümünde **`Xiaomi-Radyo-APK`** bağlantısını göreceksiniz.
3. Dokunun ve ZIP dosyasını indirin. İçindeki **`app-release.apk`** dosyasını Xiaomi telefonunuza kurun!
