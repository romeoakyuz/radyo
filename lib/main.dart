import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:audio_session/audio_session.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Bildirim ve Kilit Ekranı Medya Widget'ı
  try {
    await JustAudioBackground.init(
      androidNotificationChannelId: 'com.romeoakyuz.radyo.channel.audio',
      androidNotificationChannelName: 'Radyo Oynatıcı',
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: true,
      androidNotificationIcon: 'mipmap/ic_launcher',
    );
  } catch (e) {
    debugPrint('Background init error: $e');
  }

  runApp(const RadioApp());
}

class RadioApp extends StatelessWidget {
  const RadioApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Canlı Radyo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF38BDF8),
          surface: Color(0xFF1E293B),
        ),
        useMaterial3: true,
      ),
      home: const RadioPlayerScreen(),
    );
  }
}

class Station {
  final String id;
  final String name;
  final String url;
  final String category;

  Station({
    required this.id,
    required this.name,
    required this.url,
    required this.category,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'url': url,
    'category': category,
  };

  factory Station.fromMap(Map<String, dynamic> map) => Station(
    id: map['id'] ?? DateTime.now().millisecondsSinceEpoch.toString(),
    name: map['name'] ?? '',
    url: map['url'] ?? '',
    category: map['category'] ?? 'Genel',
  );
}

class RadioPlayerScreen extends StatefulWidget {
  const RadioPlayerScreen({super.key});

  @override
  State<RadioPlayerScreen> createState() => _RadioPlayerScreenState();
}

class _RadioPlayerScreenState extends State<RadioPlayerScreen> with WidgetsBindingObserver {
  late final AudioPlayer _player;
  late final Connectivity _connectivity;
  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;

  // İÇİNDE ÖNCEDEN YÜKLÜ HİÇBİR URL YOK - Tamamen kullanıcının ekleyeceği liste
  List<Station> _stations = [];
  int? _currentIndex;
  bool _isLoading = false;
  bool _wasPlayingBeforeDisconnect = false;
  bool _isReconnecting = false;
  double _volume = 1.0;
  Timer? _reconnectTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _player = AudioPlayer();
    _connectivity = Connectivity();
    _initApp();
  }

  Future<void> _initApp() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());

    await _loadSavedStations();

    _player.playerStateStream.listen((state) {
      if (mounted) {
        setState(() {
          _isLoading = state.processingState == ProcessingState.buffering ||
              state.processingState == ProcessingState.loading;
        });
      }
    });

    // Bağlantı koptuğunda veya hata alındığında
    _player.playbackEventStream.listen(
      (event) {},
      onError: (Object e, StackTrace stackTrace) {
        if (_player.playing && _currentIndex != null) {
          _wasPlayingBeforeDisconnect = true;
          _triggerReconnect('Bağlantı koptu, tekrar bağlanılıyor...');
        }
      },
    );

    // İnternet geri geldiğinde aynı kanalı otomatik sürdür
    _connectivitySubscription = _connectivity.onConnectivityChanged.listen((results) {
      final hasConnection = results.any((r) => r != ConnectivityResult.none);
      if (hasConnection) {
        if (_wasPlayingBeforeDisconnect && _currentIndex != null && !_player.playing) {
          _triggerReconnect('İnternet geri geldi! Radyo devam ediyor...');
        }
      } else {
        if (_player.playing) {
          _wasPlayingBeforeDisconnect = true;
          _showToast('İnternet bağlantısı kesildi. Bekleniyor...');
        }
      }
    });
  }

  void _triggerReconnect(String message) {
    if (_isReconnecting || _currentIndex == null) return;
    _isReconnecting = true;
    _showToast(message);

    _reconnectTimer?.cancel();
    _reconnectTimer = Timer.periodic(const Duration(seconds: 3), (timer) async {
      if (!mounted) {
        timer.cancel();
        return;
      }
      final connectivityList = await _connectivity.checkConnectivity();
      final hasConnection = connectivityList.any((r) => r != ConnectivityResult.none);

      if (hasConnection && _currentIndex != null) {
        try {
          await _playStation(_currentIndex!, forceReconnect: true);
          timer.cancel();
          _isReconnecting = false;
          _wasPlayingBeforeDisconnect = false;
          _showToast('Yayın tekrar bağlandı!');
        } catch (_) {}
      }
    });
  }

  Future<void> _loadSavedStations() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString('custom_radio_stations');
    if (jsonString != null && jsonString.isNotEmpty) {
      try {
        final List decoded = jsonDecode(jsonString);
        setState(() {
          _stations = decoded.map((item) => Station.fromMap(item)).toList();
        });
      } catch (e) {
        debugPrint('Yükleme hatası: $e');
      }
    }
  }

  Future<void> _saveStationsToDevice() async {
    final prefs = await SharedPreferences.getInstance();
    final encoded = jsonEncode(_stations.map((s) => s.toMap()).toList());
    await prefs.setString('custom_radio_stations', encoded);
  }

  Future<void> _playStation(int index, {bool forceReconnect = false}) async {
    if (index < 0 || index >= _stations.length) return;

    setState(() {
      _currentIndex = index;
      _isLoading = true;
    });

    try {
      final station = _stations[index];
      await _player.stop();

      final uri = Uri.parse(station.url.trim());
      final mediaItem = MediaItem(
        id: station.id,
        album: station.category,
        title: station.name,
        artist: 'Canlı Radyo',
      );

      // m3u8 veya standart yayın türünü otomatik seçme:
      final AudioSource audioSource = station.url.toLowerCase().contains('.m3u8')
          ? HlsAudioSource(uri, tag: mediaItem)
          : AudioSource.uri(uri, tag: mediaItem);

      await _player.setAudioSource(audioSource);
      await _player.play();
      _wasPlayingBeforeDisconnect = true;
      _isReconnecting = false;
      _reconnectTimer?.cancel();
    } catch (e) {
      if (!forceReconnect) {
        _showToast('Yayın açılamadı! URL adresini kontrol edin.');
      }
      setState(() => _isLoading = false);
    }
  }

  Future<void> _togglePlayPause() async {
    if (_player.playing) {
      _wasPlayingBeforeDisconnect = false;
      await _player.pause();
    } else {
      if (_currentIndex != null) {
        _wasPlayingBeforeDisconnect = true;
        await _player.play();
      }
    }
  }

  void _showToast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: const Color(0xFF0284C7),
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  void _showAddStationDialog() {
    final nameController = TextEditingController();
    final urlController = TextEditingController();
    final categoryController = TextEditingController(text: 'Müzik');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Yeni Radyo URL Ekle', style: TextStyle(color: Colors.white)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Radyo Adı (Örn: Kral FM)',
                  labelStyle: TextStyle(color: Colors.white70),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: urlController,
                decoration: const InputDecoration(
                  labelText: 'Yayın URL Adresi (http/https veya .m3u8)',
                  hintText: 'https://.../stream veya .m3u8',
                  labelStyle: TextStyle(color: Colors.white70),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: categoryController,
                decoration: const InputDecoration(
                  labelText: 'Kategori (Örn: Pop, Arabesk, Haber)',
                  labelStyle: TextStyle(color: Colors.white70),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('İptal'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF38BDF8)),
            onPressed: () {
              final name = nameController.text.trim();
              final url = urlController.text.trim();
              final cat = categoryController.text.trim();

              if (name.isNotEmpty && url.isNotEmpty) {
                final newStation = Station(
                  id: DateTime.now().millisecondsSinceEpoch.toString(),
                  name: name,
                  url: url,
                  category: cat.isEmpty ? 'Genel' : cat,
                );

                setState(() {
                  _stations.add(newStation);
                });
                _saveStationsToDevice();
                Navigator.pop(ctx);
                _playStation(_stations.length - 1);
              }
            },
            child: const Text('Kaydet ve Çal', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _confirmDeleteStation(int index) {
    final station = _stations[index];
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('İstasyonu Sil?'),
        content: Text('${station.name} radyosunu silmek istediğinize emin misiniz?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Vazgeç'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () {
              setState(() {
                if (_currentIndex == index) {
                  _player.stop();
                  _currentIndex = null;
                } else if (_currentIndex != null && _currentIndex! > index) {
                  _currentIndex = _currentIndex! - 1;
                }
                _stations.removeAt(index);
              });
              _saveStationsToDevice();
              Navigator.pop(ctx);
              _showToast('${station.name} silindi.');
            },
            child: const Text('Sil', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _connectivitySubscription?.cancel();
    _reconnectTimer?.cancel();
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final currentStation = (_currentIndex != null && _currentIndex! < _stations.length)
        ? _stations[_currentIndex!]
        : null;

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.radio, color: Color(0xFF38BDF8)),
            SizedBox(width: 8),
            Text('Canlı Radyo'),
          ],
        ),
        backgroundColor: const Color(0xFF0F172A),
        actions: [
          IconButton(
            tooltip: 'Yeni Radyo Ekle',
            icon: const Icon(Icons.add_circle, color: Color(0xFF38BDF8), size: 28),
            onPressed: _showAddStationDialog,
          ),
        ],
      ),
      body: Column(
        children: [
          if (currentStation != null)
            Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF1E293B), Color(0xFF334155)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.3),
                    blurRadius: 15,
                    offset: const Offset(0, 5),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF38BDF8).withOpacity(0.15),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.music_note, color: Color(0xFF38BDF8), size: 32),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              currentStation.name,
                              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Text(
                              currentStation.category,
                              style: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                            ),
                          ],
                        ),
                      ),
                      if (_isLoading)
                        const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2.5, color: Color(0xFF38BDF8)),
                        )
                      else if (_player.playing)
                        const Icon(Icons.graphic_eq, color: Color(0xFF38BDF8), size: 28),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Icon(_volume == 0 ? Icons.volume_off : Icons.volume_up, color: Colors.white70, size: 18),
                      Expanded(
                        child: Slider(
                          value: _volume,
                          min: 0.0,
                          max: 1.0,
                          activeColor: const Color(0xFF38BDF8),
                          inactiveColor: Colors.white24,
                          onChanged: (val) {
                            setState(() => _volume = val);
                            _player.setVolume(val);
                          },
                        ),
                      ),
                      Text('${(_volume * 100).toInt()}%', style: const TextStyle(color: Colors.white70, fontSize: 11)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      shape: const CircleBorder(),
                      padding: const EdgeInsets.all(16),
                      backgroundColor: const Color(0xFF38BDF8),
                    ),
                    onPressed: _togglePlayPause,
                    child: Icon(
                      _player.playing ? Icons.pause : Icons.play_arrow,
                      color: const Color(0xFF0F172A),
                      size: 32,
                    ),
                  ),
                ],
              ),
            ),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'RADYO İSTASYONLARINIZ',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1.2, color: Color(0xFF94A3B8)),
                ),
                Text(
                  '${_stations.length} Adet',
                  style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                ),
              ],
            ),
          ),

          if (_stations.isEmpty)
            Expanded(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.radio_outlined, size: 64, color: Colors.white.withOpacity(0.2)),
                      const SizedBox(height: 16),
                      const Text(
                        'Henüz radyo eklenmedi',
                        style: TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Sağ üstteki "+" butonuna basarak dinlemek istediğiniz radyonun adını ve yayın linkini ekleyin.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.white38, fontSize: 13),
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF38BDF8),
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        onPressed: _showAddStationDialog,
                        icon: const Icon(Icons.add, color: Colors.black),
                        label: const Text('Radyo Ekle', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
                      ),
                    ],
                  ),
                ),
              ),
            )
          else
            Expanded(
              child: ListView.builder(
                itemCount: _stations.length,
                itemBuilder: (context, index) {
                  final st = _stations[index];
                  final isSelected = index == _currentIndex;

                  return Container(
                    margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                    decoration: BoxDecoration(
                      color: isSelected ? const Color(0xFF38BDF8).withOpacity(0.12) : const Color(0xFF1E293B),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isSelected ? const Color(0xFF38BDF8) : Colors.white.withOpacity(0.05),
                        width: isSelected ? 1.5 : 1,
                      ),
                    ),
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: isSelected ? const Color(0xFF38BDF8) : const Color(0xFF334155),
                        child: Icon(
                          isSelected && _player.playing ? Icons.volume_up : Icons.radio,
                          color: isSelected ? Colors.black : Colors.white70,
                          size: 20,
                        ),
                      ),
                      title: Text(
                        st.name,
                        style: TextStyle(
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          color: isSelected ? const Color(0xFF38BDF8) : Colors.white,
                        ),
                      ),
                      subtitle: Text(
                        st.category,
                        style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12),
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (isSelected && _isLoading)
                            const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF38BDF8)),
                            )
                          else
                            IconButton(
                              icon: Icon(
                                isSelected && _player.playing ? Icons.pause_circle_filled : Icons.play_circle_fill,
                                color: isSelected ? const Color(0xFF38BDF8) : Colors.white38,
                                size: 28,
                              ),
                              onPressed: () {
                                if (isSelected) {
                                  _togglePlayPause();
                                } else {
                                  _playStation(index);
                                }
                              },
                            ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline, color: Colors.white38, size: 20),
                            tooltip: 'İstasyonu Sil',
                            onPressed: () => _confirmDeleteStation(index),
                          ),
                        ],
                      ),
                      onTap: () {
                        if (isSelected) {
                          _togglePlayPause();
                        } else {
                          _playStation(index);
                        }
                      },
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}
