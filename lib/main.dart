import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:audio_session/audio_session.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
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
}

class RadioPlayerScreen extends StatefulWidget {
  const RadioPlayerScreen({super.key});

  @override
  State<RadioPlayerScreen> createState() => _RadioPlayerScreenState();
}

class _RadioPlayerScreenState extends State<RadioPlayerScreen> {
  late final AudioPlayer _player;
  int _currentIndex = 0;
  bool _isLoading = false;
  double _volume = 1.0;

  // Radyo İstasyonları Listesi
  // Buradaki URL'leri değiştirebilir veya istediğiniz kadar yeni URL ekleyebilirsiniz:
  final List<Station> _stations = [
    Station(
      id: '1',
      name: 'Kral FM',
      url: 'https://kralfm.radyotvonline.net/kralfm',
      category: 'Arabesk',
    ),
    Station(
      id: '2',
      name: 'Power FM',
      url: 'https://powerfm.listenpowerapp.com/powerfm/mpeg/icecast.audio',
      category: 'Yabancı Hit',
    ),
    Station(
      id: '3',
      name: 'Slow Türk',
      url: 'https://radyo.duhnet.tv/slowturk',
      category: 'Türkçe Slow',
    ),
    Station(
      id: '4',
      name: 'TRT FM',
      url: 'https://trt.radyotvonline.net/trtfm',
      category: 'Pop / Karışık',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _player = AudioPlayer();
    _initAudio();
  }

  Future<void> _initAudio() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());

    _player.playerStateStream.listen((state) {
      if (mounted) {
        setState(() {
          _isLoading = state.processingState == ProcessingState.buffering ||
              state.processingState == ProcessingState.loading;
        });
      }
    });
  }

  Future<void> _playStation(int index) async {
    if (index < 0 || index >= _stations.length) return;
    setState(() {
      _currentIndex = index;
      _isLoading = true;
    });

    try {
      await _player.stop();
      await _player.setUrl(_stations[index].url);
      await _player.play();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Yayın açılamadı: ${_stations[index].name}'),
          backgroundColor: Colors.redAccent,
        ),
      );
      setState(() => _isLoading = false);
    }
  }

  void _addNewStationDialog() {
    final nameCtrl = TextEditingController();
    final urlCtrl = TextEditingController();
    final catCtrl = TextEditingController(text: 'Özel');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Yeni Radyo URL Ekle'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(labelText: 'İstasyon Adı'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: urlCtrl,
              decoration: const InputDecoration(labelText: 'Yayın URL Adresi (http/https)'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: catCtrl,
              decoration: const InputDecoration(labelText: 'Kategori'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('İptal'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF38BDF8)),
            onPressed: () {
              if (nameCtrl.text.isNotEmpty && urlCtrl.text.isNotEmpty) {
                setState(() {
                  _stations.add(Station(
                    id: DateTime.now().millisecondsSinceEpoch.toString(),
                    name: nameCtrl.text.trim(),
                    url: urlCtrl.text.trim(),
                    category: catCtrl.text.trim(),
                  ));
                });
                Navigator.pop(ctx);
                _playStation(_stations.length - 1);
              }
            },
            child: const Text('Ekle & Çal', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final current = _stations.isNotEmpty ? _stations[_currentIndex] : null;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Online Radyo (arm64-v8a)'),
        backgroundColor: const Color(0xFF0F172A),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_link, color: Color(0xFF38BDF8)),
            tooltip: 'Yeni URL Ekle',
            onPressed: _addNewStationDialog,
          ),
        ],
      ),
      body: Column(
        children: [
          if (current != null)
            Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF38BDF8).withOpacity(0.3)),
              ),
              child: Column(
                children: [
                  Text(current.name, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text(current.category, style: const TextStyle(color: Colors.white60, fontSize: 13)),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Icon(Icons.volume_up, size: 18, color: Colors.white70),
                      Expanded(
                        child: Slider(
                          value: _volume,
                          activeColor: const Color(0xFF38BDF8),
                          onChanged: (val) {
                            setState(() => _volume = val);
                            _player.setVolume(val);
                          },
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          shape: const CircleBorder(),
                          padding: const EdgeInsets.all(18),
                          backgroundColor: const Color(0xFF38BDF8),
                        ),
                        onPressed: () {
                          if (_player.playing) {
                            _player.pause();
                          } else {
                            _playStation(_currentIndex);
                          }
                        },
                        child: _isLoading
                            ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black))
                            : Icon(_player.playing ? Icons.pause : Icons.play_arrow, color: Colors.black, size: 30),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: _stations.length,
              itemBuilder: (ctx, i) {
                final st = _stations[i];
                final isSelected = i == _currentIndex;
                return ListTile(
                  leading: Icon(
                    isSelected && _player.playing ? Icons.volume_up : Icons.radio,
                    color: isSelected ? const Color(0xFF38BDF8) : Colors.white60,
                  ),
                  title: Text(st.name, style: TextStyle(color: isSelected ? const Color(0xFF38BDF8) : Colors.white)),
                  subtitle: Text(st.category, style: const TextStyle(color: Colors.white38, fontSize: 12)),
                  trailing: Icon(isSelected && _player.playing ? Icons.pause : Icons.play_arrow),
                  onTap: () => _playStation(i),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
