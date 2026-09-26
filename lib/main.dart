import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:just_audio/just_audio.dart';
import 'package:audio_service/audio_service.dart';
import 'package:home_widget/home_widget.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Global AudioHandler örneği (Xiaomi RAM optimizasyonuna dayanıklı Foreground Service)
late AudioHandler _audioHandler;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Bildirim çubuğu ve Xiaomi Foreground Service başlatma
  _audioHandler = await AudioService.init(
    builder: () => RadioAudioHandler(),
    config: const AudioServiceConfig(
      androidNotificationChannelId: 'com.xiaomi.radyo.channel.audio',
      androidNotificationChannelName: 'Xiaomi Canlı Radyo Çalar',
      androidNotificationChannelDescription: 'Arka planda kesintisiz radyo yayını',
      androidNotificationOngoing: true, // Çalarken bildirimi sabitler
      androidStopForegroundOnPause: true, // PAUSE YAPILINCA BİLDİRİMİ TAMAMEN SİLER
      androidShowNotificationBadge: true,
      androidNotificationClickStartsActivity: true,
      androidNotificationIcon: 'drawable/ic_stat_radio',
    ),
  );

  runApp(const XiaomiRadioApp());
}

/// Radyo Kanalı Veri Modeli
class RadioStation {
  final String id;
  final String name;
  final String frequency;
  final String url;
  final String genre;
  final int colorValue;

  RadioStation({
    required this.id,
    required this.name,
    required this.frequency,
    required this.url,
    required this.genre,
    required this.colorValue,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'frequency': frequency,
    'url': url,
    'genre': genre,
    'colorValue': colorValue,
  };

  factory RadioStation.fromMap(Map<String, dynamic> map) => RadioStation(
    id: map['id'] ?? '',
    name: map['name'] ?? '',
    frequency: map['frequency'] ?? '',
    url: map['url'] ?? '',
    genre: map['genre'] ?? '',
    colorValue: map['colorValue'] ?? 0xFFE11D48,
  );
}

/// Varsayılan 3 İstasyon: Kral FM, Süper FM, Alem FM
final List<RadioStation> defaultStations = [
  RadioStation(
    id: 'kral-fm',
    name: 'Kral FM',
    frequency: '92.0 MHz',
    url: 'https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8',
    genre: 'Damar & Arabesk',
    colorValue: 0xFFE11D48, // Kral Kırmızısı
  ),
  RadioStation(
    id: 'super-fm',
    name: 'Süper FM',
    frequency: '90.8 MHz',
    url: 'https://playerservices.streamtheworld.com/api/livestream-redirect/SUPER_FM_SC',
    genre: 'Türkçe Pop & Hit',
    colorValue: 0xFFF59E0B, // Süper Sarı/Turuncu
  ),
  RadioStation(
    id: 'alem-fm',
    name: 'Alem FM',
    frequency: '89.2 MHz',
    url: 'https://turkmedya.radyotvonline.net/alemfmaac',
    genre: 'Pop & Eğlence',
    colorValue: 0xFF2563EB, // Alem Mavisi
  ),
];

/// Xiaomi Arka Plan Audio Handler (Bildirim, Play/Pause, Next & Widget Kontrolü)
class RadioAudioHandler extends BaseAudioHandler with SeekHandler {
  final AudioPlayer _player = AudioPlayer();
  RadioStation? currentStation;
  List<RadioStation> stations = [...defaultStations];
  int currentIndex = 0;
  
  int bufferSeconds = 3;
  int reconnectDelaySeconds = 5;

  RadioAudioHandler() {
    _initAudioPipeline();
  }

  void _initAudioPipeline() {
    // Çalma durumu dinleyicisi -> Bildirim ve Widget güncelleme
    _player.playerStateStream.listen((state) {
      final isPlaying = state.playing;
      final processingState = state.processingState;

      playbackState.add(playbackState.value.copyWith(
        controls: [
          // Bildirimde sadece Play/Pause ve Next tuşları istenildi
          if (isPlaying) MediaControl.pause else MediaControl.play,
          MediaControl.skipToNext,
        ],
        systemActions: const {
          MediaAction.play,
          MediaAction.pause,
          MediaAction.skipToNext,
          MediaAction.stop,
        },
        androidCompactActionIndices: const [0, 1], // Play/Pause ve Next
        playing: isPlaying,
        processingState: const {
          ProcessingState.idle: AudioProcessingState.idle,
          ProcessingState.loading: AudioProcessingState.loading,
          ProcessingState.buffering: AudioProcessingState.buffering,
          ProcessingState.ready: AudioProcessingState.ready,
          ProcessingState.completed: AudioProcessingState.completed,
        }[processingState]!,
      ));

      // Xiaomi 4x1 Widget durumunu senkronize et
      _syncHomeWidget(isPlaying);
    });
  }

  void setStationList(List<RadioStation> list, int index) {
    stations = list;
    if (index >= 0 && index < stations.length) {
      currentIndex = index;
    }
  }

  Future<void> playStation(RadioStation station) async {
    currentStation = station;
    mediaItem.add(MediaItem(
      id: station.id,
      album: 'Xiaomi Canlı Radyo',
      title: station.name,
      artist: '${station.frequency} - ${station.genre}',
      artUri: Uri.parse('https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300'),
    ));

    try {
      await _player.stop();
      // Buffer ve URL yüklemesi
      await _player.setAudioSource(
        AudioSource.uri(Uri.parse(station.url)),
        preload: true,
      );
      await _player.play();
    } catch (e) {
      debugPrint("Yayın başlatma hatası: $e");
    }
  }

  @override
  Future<void> play() async {
    if (currentStation == null && stations.isNotEmpty) {
      await playStation(stations[currentIndex]);
    } else {
      await _player.play();
    }
  }

  @override
  Future<void> pause() async {
    // Kullanıcı talebi: PAUSE YAPINCA BİLDİRİM TAMAMEN TEMİZLENSİN
    await stop();
  }

  @override
  Future<void> stop() async {
    await _player.stop();
    mediaItem.add(null); // Android bildirimini anında temizle
    playbackState.add(playbackState.value.copyWith(
      playing: false,
      controls: [],
      processingState: AudioProcessingState.idle,
    ));
    _syncHomeWidget(false);
    await super.stop(); // Foreground notification servisini kapatıp bildirimi kaldırır
  }

  @override
  Future<void> skipToNext() async {
    if (stations.isEmpty) return;
    currentIndex = (currentIndex + 1) % stations.length;
    await playStation(stations[currentIndex]);
  }

  /// Xiaomi 4x1 Home Screen Widget güncellemesi
  Future<void> _syncHomeWidget(bool isPlaying) async {
    try {
      final name = currentStation?.name ?? 'Kanal Seçilmedi';
      final freq = currentStation?.frequency ?? '';
      
      await HomeWidget.saveWidgetData<String>('widget_station_name', name);
      await HomeWidget.saveWidgetData<String>('widget_station_freq', freq);
      await HomeWidget.saveWidgetData<bool>('widget_is_playing', isPlaying);
      
      await HomeWidget.updateWidget(
        name: 'RadioAppWidgetProvider',
        androidName: 'RadioAppWidgetProvider',
      );
    } catch (e) {
      debugPrint("Widget güncelleme hatası: $e");
    }
  }
}

class XiaomiRadioApp extends StatelessWidget {
  const XiaomiRadioApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Xiaomi Canlı Radyo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFFE11D48),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const MainRadioScreen(),
    );
  }
}

/// Ana Ekran: Canlı Yayın ve Ayarlar sekmeleri
class MainRadioScreen extends StatefulWidget {
  const MainRadioScreen({super.key});

  @override
  State<MainRadioScreen> createState() => _MainRadioScreenState();
}

class _MainRadioScreenState extends State<MainRadioScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  
  List<RadioStation> stations = [...defaultStations];
  int currentIndex = 0;
  bool isPlaying = false;
  
  // Kullanıcının ayarlardan gireceği süreler
  int reconnectDelaySeconds = 5;
  int bufferSeconds = 3;
  
  // İnternet kopma ve geri sayım durumu (Ana ekranda sayaç)
  bool isReconnecting = false;
  int countdownSeconds = 0;
  Timer? _countdownTimer;
  StreamSubscription? _connectivitySub;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadPreferences();
    _initConnectivityListener();
    _initAudioServiceListener();
    _initWidgetLaunch();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _countdownTimer?.cancel();
    _connectivitySub?.cancel();
    super.dispose();
  }

  Future<void> _loadPreferences() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      reconnectDelaySeconds = prefs.getInt('reconnect_delay') ?? 5;
      bufferSeconds = prefs.getInt('buffer_seconds') ?? 3;
      currentIndex = prefs.getInt('last_station_index') ?? 0;
      
      final savedStationsJson = prefs.getString('custom_stations');
      if (savedStationsJson != null) {
        try {
          final List decoded = jsonDecode(savedStationsJson);
          stations = decoded.map((e) => RadioStation.fromMap(e)).toList();
        } catch (_) {}
      }
    });

    // AudioHandler'a kanalları aktar
    final handler = _audioHandler as RadioAudioHandler;
    handler.setStationList(stations, currentIndex);
  }

  Future<void> _savePreferences() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('reconnect_delay', reconnectDelaySeconds);
    await prefs.setInt('buffer_seconds', bufferSeconds);
    await prefs.setInt('last_station_index', currentIndex);
    await prefs.setString(
      'custom_stations',
      jsonEncode(stations.map((s) => s.toMap()).toList()),
    );
  }

  /// İnternet Kesilme & Geri Gelme Takibi (Kullanıcı Şartı):
  /// İnternet kesilince girdiğiniz süre boyunca hazırda bekler.
  /// İnternet süre dolmadan gelirse HEMEN çalar; süre dolarsa radyo STOP olur.
  void _initConnectivityListener() {
    _connectivitySub = Connectivity().onConnectivityChanged.listen((List<ConnectivityResult> results) {
      final isOffline = results.isEmpty || results.contains(ConnectivityResult.none);
      
      if (isOffline) {
        if (isPlaying) {
          _audioHandler.pause();
          setState(() {
            isReconnecting = true;
            countdownSeconds = reconnectDelaySeconds;
          });
          _startCountdownTimeout();
        }
      } else {
        // İnternet geri geldi!
        if (isReconnecting) {
          // KULLANICI ŞARTI: Beklemeden HEMEN çalmaya başla!
          _countdownTimer?.cancel();
          setState(() {
            isReconnecting = false;
            countdownSeconds = 0;
          });
          final handler = _audioHandler as RadioAudioHandler;
          handler.playStation(stations[currentIndex]);
        }
      }
    });
  }

  void _startCountdownTimeout() {
    _countdownTimer?.cancel();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      if (countdownSeconds > 1) {
        setState(() {
          countdownSeconds--;
        });
      } else {
        // KULLANICI ŞARTI: Girdiğim süre sonunda internet gelmezse STOP olacak.
        timer.cancel();
        setState(() {
          isReconnecting = false;
          countdownSeconds = 0;
        });
        final handler = _audioHandler as RadioAudioHandler;
        handler.stop(); // Radyoyu tamamen durdur ve bildirimi kaldır
      }
    });
  }

  void _initAudioServiceListener() {
    _audioHandler.playbackState.listen((state) {
      if (mounted) {
        setState(() {
          isPlaying = state.playing;
        });
      }
    });
  }

  void _initWidgetLaunch() {
    HomeWidget.initiallyLaunchedFromHomeWidget().then((uri) {
      if (uri != null && uri.host == 'play_next') {
        _playNext();
      }
    });
    HomeWidget.widgetClicked.listen((uri) {
      if (uri != null && uri.host == 'play_next') {
        _playNext();
      }
    });
  }

  void _togglePlayPause() {
    if (isPlaying) {
      _audioHandler.pause();
    } else {
      final handler = _audioHandler as RadioAudioHandler;
      handler.playStation(stations[currentIndex]);
    }
  }

  void _playNext() {
    if (stations.isEmpty) return;
    setState(() {
      currentIndex = (currentIndex + 1) % stations.length;
    });
    _savePreferences();
    final handler = _audioHandler as RadioAudioHandler;
    handler.playStation(stations[currentIndex]);
  }

  void _playPrevious() {
    if (stations.isEmpty) return;
    setState(() {
      currentIndex = (currentIndex - 1 + stations.length) % stations.length;
    });
    _savePreferences();
    final handler = _audioHandler as RadioAudioHandler;
    handler.playStation(stations[currentIndex]);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFFFF6900), // Xiaomi Turuncusu
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text('XIAOMI', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.white)),
            ),
            const SizedBox(width: 8),
            const Text('Canlı Radyo', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          ],
        ),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: const Color(0xFFFF6900),
          labelColor: Colors.white,
          unselectedLabelColor: Colors.grey,
          tabs: const [
            Tab(icon: Icon(Icons.radio), text: 'Canlı Yayın'),
            Tab(icon: Icon(Icons.settings), text: 'Ayarlar'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // 1. SEKME: CANLI YAYIN
          _buildLiveBroadcastView(),
          
          // 2. SEKME: AYARLAR (Kanal Ekle/Sil, Süre Kutusları)
          _buildSettingsView(),
        ],
      ),
    );
  }

  Widget _buildLiveBroadcastView() {
    final currentStation = stations.isNotEmpty ? stations[currentIndex] : null;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          // İNTERNET GERİ SAYIM BANNERI (KULLANICI ŞARTI: ANA EKRANDA SAYSIN)
          if (isReconnecting)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFFEF4444).withOpacity(0.2),
                border: Border.all(color: const Color(0xFFEF4444), width: 2),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                children: [
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.wifi_off, color: Color(0xFFEF4444), size: 24),
                      SizedBox(width: 8),
                      Text(
                        'İnternet Bağlantısı Bekleniyor!',
                        style: TextStyle(color: Color(0xFFEF4444), fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Son kanal ${countdownSeconds} saniye içinde tekrar çalacak...',
                    style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  LinearProgressIndicator(
                    value: reconnectDelaySeconds > 0 ? (countdownSeconds / reconnectDelaySeconds) : 0,
                    backgroundColor: Colors.white24,
                    color: const Color(0xFFEF4444),
                  ),
                ],
              ),
            ),

          // RADYO KARTI
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Color(currentStation?.colorValue ?? 0xFFE11D48).withOpacity(0.8),
                  const Color(0xFF1E293B),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: Color(currentStation?.colorValue ?? 0xFFE11D48).withOpacity(0.3),
                  blurRadius: 20,
                  offset: const Offset(0, 8),
                )
              ],
            ),
            child: Column(
              children: [
                // Canlı rozeti
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    color: isPlaying ? Colors.red : Colors.grey.shade800,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isPlaying ? Colors.white : Colors.grey,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        isPlaying ? 'CANLI YAYIN' : 'DURAKLATILDI',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: Colors.white),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                
                // Kanal Adı & Frekansı
                Text(
                  currentStation?.name ?? 'Kanal Yok',
                  style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w900, color: Colors.white),
                ),
                const SizedBox(height: 4),
                Text(
                  currentStation?.frequency ?? '',
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: Colors.white70),
                ),
                const SizedBox(height: 8),
                Text(
                  currentStation?.genre ?? '',
                  style: const TextStyle(fontSize: 14, color: Colors.white60),
                ),
                const SizedBox(height: 24),

                // SES KONTROL DÜĞMELERİ (BÜYÜK VE ARASI AÇIK - KULLANICI ŞARTI)
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Önceki Kanal Tuşu
                    IconButton.filled(
                      style: IconButton.styleFrom(
                        backgroundColor: Colors.white24,
                        padding: const EdgeInsets.all(16),
                        minimumSize: const Size(60, 60),
                      ),
                      onPressed: _playPrevious,
                      icon: const Icon(Icons.skip_previous, size: 30, color: Colors.white),
                    ),
                    const SizedBox(width: 32), // ARASI AÇIK

                    // BÜYÜK PLAY / PAUSE DÜĞMESİ
                    IconButton.filled(
                      style: IconButton.styleFrom(
                        backgroundColor: Colors.white,
                        padding: const EdgeInsets.all(20),
                        minimumSize: const Size(76, 76),
                      ),
                      onPressed: _togglePlayPause,
                      icon: Icon(
                        isPlaying ? Icons.pause : Icons.play_arrow,
                        size: 42,
                        color: Colors.black,
                      ),
                    ),
                    const SizedBox(width: 32), // ARASI AÇIK

                    // Sonraki Kanal Tuşu
                    IconButton.filled(
                      style: IconButton.styleFrom(
                        backgroundColor: Colors.white24,
                        padding: const EdgeInsets.all(16),
                        minimumSize: const Size(60, 60),
                      ),
                      onPressed: _playNext,
                      icon: const Icon(Icons.skip_next, size: 30, color: Colors.white),
                    ),
                  ],
                ),
              ],
            ),
          ),
          
          const SizedBox(height: 24),
          
          // 4x1 ANA EKRAN WIDGET ÖNİZLEMESİ (XIAOMI HOMESCREEN FORMATINDA)
          _build4x1WidgetPreview(currentStation),

          const SizedBox(height: 16),
          // Hızlı Süre Göstergeleri
          Row(
            children: [
              Expanded(
                child: _buildInfoBadge(
                  Icons.wifi,
                  'İnternet Bekleme',
                  '${reconnectDelaySeconds} sn',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildInfoBadge(
                  Icons.timer,
                  'Tampon (Buffer)',
                  '${bufferSeconds} sn',
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// 4x1 Xiaomi Widget Önizleme Kartı (Büyük ve aralıklı tuşlar)
  Widget _build4x1WidgetPreview(RadioStation? station) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
          child: Row(
            children: [
              Icon(Icons.widgets, size: 18, color: Color(0xFFFF6900)),
              SizedBox(width: 6),
              Text(
                'Xiaomi 4x1 Ana Ekran Widget Önizlemesi',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white70),
              ),
            ],
          ),
        ),
        Container(
          width: double.infinity,
          height: 90, // Standart Android 4x1 Hücre Oranı
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          decoration: BoxDecoration(
            color: const Color(0xFF1E293B).withOpacity(0.95),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: Colors.white12, width: 1.5),
            boxShadow: const [
              BoxShadow(color: Colors.black45, blurRadius: 10, offset: Offset(0, 4)),
            ],
          ),
          child: Row(
            children: [
              // Logo & Kanal Adı
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: Color(station?.colorValue ?? 0xFFE11D48),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.radio, color: Colors.white, size: 24),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      station?.name ?? 'Kanal Seçilmedi',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
                    ),
                    Text(
                      station?.frequency ?? 'Canlı Akış',
                      style: const TextStyle(fontSize: 12, color: Colors.white60),
                    ),
                  ],
                ),
              ),

              // BÜYÜK VE ARASI AÇIK WIDGET DÜĞMELERİ (KULLANICI TALEBİ)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Play/Pause Tuşu
                  GestureDetector(
                    onTap: _togglePlayPause,
                    child: Container(
                      width: 52,
                      height: 52,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: Color(0xFFFF6900), // Xiaomi Turuncu
                      ),
                      child: Icon(
                        isPlaying ? Icons.pause : Icons.play_arrow,
                        color: Colors.white,
                        size: 32,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16), // ARALIKLI DOKUNMASI KOLAY
                  // Next Tuşu
                  GestureDetector(
                    onTap: _playNext,
                    child: Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: Colors.white.withOpacity(0.15),
                      ),
                      child: const Icon(
                        Icons.skip_next,
                        color: Colors.white,
                        size: 32,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildInfoBadge(IconData icon, String title, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        children: [
          Icon(icon, color: const Color(0xFFFF6900), size: 22),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              Text(value, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
            ],
          ),
        ],
      ),
    );
  }

  // 2. SEKME: AYARLAR (KANAL EKLE / SİL, SÜRE KUTUCUKLARI)
  Widget _buildSettingsView() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // KUTU İÇİNE ZAMAN GİRİŞLERİ (KULLANICI ŞARTI)
          const Text(
            'Zamanlama ve Tampon Ayarları',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
          ),
          const SizedBox(height: 12),
          
          // 1. KUTU: İnternet Bekleme Zamanı
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white10),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'İnternet Bekleme Zamanı (Saniye)',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white),
                ),
                const SizedBox(height: 4),
                const Text(
                  'İnternet koptuğunda ana ekranda sayılacak ve tekrar bağlanılacak süre.',
                  style: TextStyle(fontSize: 12, color: Colors.grey),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    IconButton.filled(
                      style: IconButton.styleFrom(backgroundColor: Colors.white12),
                      onPressed: () {
                        if (reconnectDelaySeconds > 1) {
                          setState(() => reconnectDelaySeconds--);
                          _savePreferences();
                        }
                      },
                      icon: const Icon(Icons.remove),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF0F172A),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: const Color(0xFFFF6900)),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '$reconnectDelaySeconds saniye',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    IconButton.filled(
                      style: IconButton.styleFrom(backgroundColor: Colors.white12),
                      onPressed: () {
                        setState(() => reconnectDelaySeconds++);
                        _savePreferences();
                      },
                      icon: const Icon(Icons.add),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 2. KUTU: Buffer Zamanı
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white10),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Buffer (Tampon Bellek) Zamanı (Saniye)',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Yayın başlatılmadan önce belleğe alınacak ses tampon süresi.',
                  style: TextStyle(fontSize: 12, color: Colors.grey),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    IconButton.filled(
                      style: IconButton.styleFrom(backgroundColor: Colors.white12),
                      onPressed: () {
                        if (bufferSeconds > 1) {
                          setState(() => bufferSeconds--);
                          _savePreferences();
                        }
                      },
                      icon: const Icon(Icons.remove),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF0F172A),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: const Color(0xFFFF6900)),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '$bufferSeconds saniye',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    IconButton.filled(
                      style: IconButton.styleFrom(backgroundColor: Colors.white12),
                      onPressed: () {
                        setState(() => bufferSeconds++);
                        _savePreferences();
                      },
                      icon: const Icon(Icons.add),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // KANAL YÖNETİMİ: EKLE VE SİL (KULLANICI ŞARTI)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Kanal Yönetimi (Ekle & Sil)',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
              ),
              FilledButton.icon(
                style: FilledButton.styleFrom(backgroundColor: const Color(0xFFFF6900)),
                onPressed: _showAddStationDialog,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Kanal Ekle'),
              ),
            ],
          ),
          const SizedBox(height: 12),

          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: stations.length,
            itemBuilder: (context, index) {
              final station = stations[index];
              return Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFF1E293B),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: index == currentIndex ? Color(station.colorValue) : Colors.transparent,
                    width: 2,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: Color(station.colorValue),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        station.name.substring(0, 1),
                        style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(station.name, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                          Text('${station.frequency} - ${station.genre}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
                      onPressed: () {
                        if (stations.length <= 1) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('En az bir radyo kanalı kalmalıdır!')),
                          );
                          return;
                        }
                        setState(() {
                          stations.removeAt(index);
                          if (currentIndex >= stations.length) {
                            currentIndex = 0;
                          }
                        });
                        _savePreferences();
                      },
                    ),
                  ],
                ),
              );
            },
          ),

          const SizedBox(height: 24),
          // XIAOMI / MIUI RAM & ARKA PLAN AYARLARI
          _buildXiaomiGuideCard(),
        ],
      ),
    );
  }

  void _showAddStationDialog() {
    final nameCtrl = TextEditingController();
    final freqCtrl = TextEditingController(text: '100.0 MHz');
    final urlCtrl = TextEditingController();
    final genreCtrl = TextEditingController(text: 'Türkçe Müzik');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Yeni Kanal Ekle', style: TextStyle(color: Colors.white)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Kanal Adı (Örn: Power Türk)', labelStyle: TextStyle(color: Colors.grey)),
              ),
              TextField(
                controller: freqCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Frekans (Örn: 99.8 MHz)', labelStyle: TextStyle(color: Colors.grey)),
              ),
              TextField(
                controller: urlCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Yayın URL (.m3u8 veya mp3)', labelStyle: TextStyle(color: Colors.grey)),
              ),
              TextField(
                controller: genreCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Müzik Türü', labelStyle: TextStyle(color: Colors.grey)),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('İptal', style: TextStyle(color: Colors.grey)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFFF6900)),
            onPressed: () {
              if (nameCtrl.text.isNotEmpty && urlCtrl.text.isNotEmpty) {
                setState(() {
                  stations.add(RadioStation(
                    id: 'station_${DateTime.now().millisecondsSinceEpoch}',
                    name: nameCtrl.text.trim(),
                    frequency: freqCtrl.text.trim(),
                    url: urlCtrl.text.trim(),
                    genre: genreCtrl.text.trim(),
                    colorValue: 0xFF10B981,
                  ));
                });
                _savePreferences();
                Navigator.pop(ctx);
              }
            },
            child: const Text('Ekle'),
          ),
        ],
      ),
    );
  }

  Widget _buildXiaomiGuideCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFF6900).withOpacity(0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFFF6900).withOpacity(0.4)),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.security, color: Color(0xFFFF6900)),
              SizedBox(width: 8),
              Text(
                'Xiaomi (MIUI/HyperOS) RAM Koruması',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white),
              ),
            ],
          ),
          SizedBox(height: 8),
          Text(
            'RAM temizlendiğinde radyonun susmaması için:\n'
            '1. Son uygulamalar ekranında uygulamaya basılı tutup Kilit simgesine dokunun.\n'
            '2. Ayarlar > Uygulamalar > İzinler > Otomatik Başlatma\'yı açık yapın.\n'
            '3. Pil Tasarrufu ayarını "Kısıtlama Yok" olarak belirleyin.',
            style: TextStyle(color: Colors.white70, fontSize: 12, height: 1.5),
          ),
        ],
      ),
    );
  }
}
