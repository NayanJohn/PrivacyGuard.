import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:privacyguard/channel/native_bridge.dart';

void main() {
  runApp(const PrivacyGuardApp());
}

class PrivacyGuardApp extends StatelessWidget {
  const PrivacyGuardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PrivacyGuard',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0B1020),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF22D3EE),
          secondary: Color(0xFF8B5CF6),
          surface: Color(0xFF151D30),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF10172A),
          elevation: 0,
        ),
      ),
      home: const MainAuditScreen(),
    );
  }
}

class MainAuditScreen extends StatefulWidget {
  const MainAuditScreen({super.key});

  @override
  State<MainAuditScreen> createState() => _MainAuditScreenState();
}

class _MainAuditScreenState extends State<MainAuditScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  bool _loading = true;
  String? _errorMessage;

  bool _usageAccess = false;
  bool _shizukuRunning = false;
  bool _shizukuPermission = false;
  bool _deviceOwner = false;
  int _apiLevel = 0;

  // P1 Lifecycle state
  final TextEditingController _lifecyclePkgController =
      TextEditingController(text: 'com.privacyguard.app');
  bool _queryingWindows = false;
  List<Map<String, dynamic>> _foregroundWindows = <Map<String, dynamic>>[];
  bool _hasQueriedWindows = false;

  // P2 Baseline state
  final TextEditingController _baselinePkgController =
      TextEditingController(text: 'com.privacyguard.app');
  bool _queryingBaseline = false;
  Map<String, dynamic>? _baselineData;
  bool _hasQueriedBaseline = false;

  // P3 Access Tracking state
  final TextEditingController _accessEventsPkgController =
      TextEditingController(text: 'com.android.chrome');
  bool _queryingAccessEvents = false;
  List<Map<String, dynamic>> _accessEvents = <Map<String, dynamic>>[];
  bool _hasQueriedAccessEvents = false;
  int _selectedSinceHours = 1;

  // P4 Scoring state
  final TextEditingController _scoringPkgController =
      TextEditingController(text: 'com.android.chrome');
  bool _runningScan = false;
  Map<String, dynamic>? _scanResult;
  bool _hasScanned = false;
  int _userAge = 25;
  bool _hasParentLink = false;
  bool _hardBlockingEnabled = true;

  // P5 Background Audit & History state
  final TextEditingController _historyPkgController =
      TextEditingController(text: 'com.android.chrome');
  bool _queryingHistory = false;
  bool _triggeringAudit = false;
  Map<String, dynamic>? _auditStatus;
  List<Map<String, dynamic>> _scoreHistory = <Map<String, dynamic>>[];
  List<Map<String, dynamic>> _timelineEvents = <Map<String, dynamic>>[];
  String? _auditBannerMessage;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
    _refreshStatus();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _lifecyclePkgController.dispose();
    _baselinePkgController.dispose();
    _accessEventsPkgController.dispose();
    _scoringPkgController.dispose();
    _historyPkgController.dispose();
    super.dispose();
  }


  Future<void> _refreshStatus() async {
    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final status = await NativeBridge.getAccessStatus();
      setState(() {
        _usageAccess = status['usageAccess'] as bool? ?? false;
        _shizukuRunning = status['shizukuRunning'] as bool? ?? false;
        _shizukuPermission = status['shizukuPermission'] as bool? ?? false;
        _deviceOwner = status['deviceOwner'] as bool? ?? false;
        _apiLevel = (status['apiLevel'] as num?)?.toInt() ?? 0;
        _loading = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = 'PlatformException: [${e.code}] ${e.message}';
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Error: $e';
        _loading = false;
      });
    }

    _fetchAuditStatus();
    _queryHistoryAndTimeline();
  }

  Future<void> _fetchAuditStatus() async {
    try {
      final status = await NativeBridge.getAuditStatus();
      if (mounted) {
        setState(() {
          _auditStatus = status;
        });
      }
    } catch (_) {}
  }

  Future<void> _togglePeriodicSchedule() async {
    final current = _auditStatus?['isPeriodicScheduled'] as bool? ?? false;
    try {
      if (current) {
        await NativeBridge.cancelPeriodicAudit();
      } else {
        await NativeBridge.schedulePeriodicAudit(intervalMinutes: 15);
      }
      await _fetchAuditStatus();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to update periodic schedule: $e')),
      );
    }
  }

  Future<void> _triggerImmediateAudit() async {
    setState(() {
      _triggeringAudit = true;
      _auditBannerMessage = null;
    });

    try {
      final pkg = _historyPkgController.text.trim();
      final res = await NativeBridge.triggerAudit(
        packages: pkg.isNotEmpty ? [pkg] : null,
      );
      final msg = res['message'] as String? ?? 'Audit cycle complete';
      if (mounted) {
        setState(() {
          _triggeringAudit = false;
          _auditBannerMessage = msg;
        });
      }
      await _queryHistoryAndTimeline();
      await _fetchAuditStatus();
    } catch (e) {
      if (mounted) {
        setState(() {
          _triggeringAudit = false;
          _auditBannerMessage = 'Audit error: $e';
        });
      }
    }
  }

  Future<void> _queryHistoryAndTimeline() async {
    final pkg = _historyPkgController.text.trim();
    if (pkg.isEmpty) return;

    setState(() {
      _queryingHistory = true;
    });

    try {
      final history = await NativeBridge.getAuditHistory(pkg);
      final timeline = await NativeBridge.getTimelineEvents(pkg: pkg);
      if (mounted) {
        setState(() {
          _scoreHistory = history;
          _timelineEvents = timeline;
          _queryingHistory = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _queryingHistory = false;
        });
      }
    }
  }


  Future<void> _openUsageSettings() async {
    try {
      await NativeBridge.openUsageAccessSettings();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to open settings: $e')),
      );
    }
  }

  Future<void> _queryForegroundWindows() async {
    final pkg = _lifecyclePkgController.text.trim();
    if (pkg.isEmpty) return;

    setState(() {
      _queryingWindows = true;
      _hasQueriedWindows = true;
      _errorMessage = null;
    });

    final now = DateTime.now().millisecondsSinceEpoch;
    final past24Hours = now - (24 * 60 * 60 * 1000);

    try {
      final windows = await NativeBridge.getForegroundWindows(
        pkg: pkg,
        startMs: past24Hours,
        endMs: now,
      );
      setState(() {
        _foregroundWindows = windows;
        _queryingWindows = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = 'Query failed: [${e.code}] ${e.message}';
        _queryingWindows = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Query failed: $e';
        _queryingWindows = false;
      });
    }
  }

  Future<void> _queryPermissionBaseline(String pkg) async {
    final target = pkg.trim();
    if (target.isEmpty) return;

    setState(() {
      _queryingBaseline = true;
      _hasQueriedBaseline = true;
      _errorMessage = null;
      _baselinePkgController.text = target;
    });

    try {
      final data = await NativeBridge.getBaseline(target);
      setState(() {
        _baselineData = data;
        _queryingBaseline = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = 'Baseline query failed: [${e.code}] ${e.message}';
        _queryingBaseline = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Baseline query failed: $e';
        _queryingBaseline = false;
      });
    }
  }

  Future<void> _requestShizukuPermission() async {
    try {
      await NativeBridge.requestShizukuPermission();
      await Future.delayed(const Duration(milliseconds: 600));
      await _refreshStatus();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to request Shizuku permission: $e')),
      );
    }
  }

  Future<void> _queryAccessEvents([String? pkgOverride]) async {
    final pkg = (pkgOverride ?? _accessEventsPkgController.text).trim();
    if (pkg.isEmpty) return;
    if (pkgOverride != null) {
      _accessEventsPkgController.text = pkg;
    }

    setState(() {
      _queryingAccessEvents = true;
      _hasQueriedAccessEvents = true;
      _errorMessage = null;
    });

    final now = DateTime.now().millisecondsSinceEpoch;
    final sinceMs = _selectedSinceHours > 0
        ? now - (_selectedSinceHours * 60 * 60 * 1000)
        : 0;

    try {
      final events = await NativeBridge.getAccessEvents(
        pkg: pkg,
        sinceMs: sinceMs,
      );
      setState(() {
        _accessEvents = events;
        _queryingAccessEvents = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = 'AppOps query failed: [${e.code}] ${e.message}';
        _queryingAccessEvents = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'AppOps query failed: $e';
        _queryingAccessEvents = false;
      });
    }
  }

  Future<void> _runAuditScan([String? pkgOverride]) async {
    final pkg = (pkgOverride ?? _scoringPkgController.text).trim();
    if (pkg.isEmpty) return;
    if (pkgOverride != null) {
      _scoringPkgController.text = pkg;
    }

    setState(() {
      _runningScan = true;
      _hasScanned = true;
      _errorMessage = null;
    });

    try {
      final res = await NativeBridge.runScan(
        pkg,
        userAge: _userAge,
        hasParentLink: _hasParentLink,
        hardBlockingEnabled: _hardBlockingEnabled,
      );
      setState(() {
        _scanResult = res;
        _runningScan = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = 'Scan failed: [${e.code}] ${e.message}';
        _runningScan = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Scan failed: $e';
        _runningScan = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'PrivacyGuard Auditor',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Re-check status',
            onPressed: _refreshStatus,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          indicatorColor: const Color(0xFF22D3EE),
          labelColor: const Color(0xFF22D3EE),
          unselectedLabelColor: const Color(0xFF94A3B8),
          tabs: const [
            Tab(icon: Icon(Icons.timeline), text: 'Lifecycle (P1)'),
            Tab(icon: Icon(Icons.security), text: 'Permissions (P2)'),
            Tab(icon: Icon(Icons.privacy_tip_outlined), text: 'Access Events (P3)'),
            Tab(icon: Icon(Icons.analytics_outlined), text: 'Scoring (P4)'),
            Tab(icon: Icon(Icons.history_rounded), text: 'History (P5)'),
          ],

        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                _buildLifecycleTab(),
                _buildBaselineTab(),
                _buildAccessEventsTab(),
                _buildScoringTab(),
                _buildHistoryTab(),
              ],
            ),

    );
  }

  Widget _buildLifecycleTab() {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (_errorMessage != null) _buildErrorBanner(),

        // System Capabilities Card
        _buildCapabilitiesCard(),

        const SizedBox(height: 20),

        // Lifecycle Query Card
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Audit Foreground Windows (UsageEvents)',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF22D3EE),
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Queries UsageStatsManager.queryEvents for ACTIVITY_RESUMED (1) and ACTIVITY_STOPPED (23), building time-ordered sessions.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: _lifecyclePkgController,
                  decoration: InputDecoration(
                    labelText: 'Package Name',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    filled: true,
                    fillColor: const Color(0xFF0B1020),
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: const Color(0xFF0B1020),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    onPressed: _queryingWindows ? null : _queryForegroundWindows,
                    icon: _queryingWindows
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.search),
                    label: Text(
                      _queryingWindows ? 'Querying...' : 'Query Sessions (Last 24h)',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 20),

        // Results Section
        const Text(
          'Observed Foreground Sessions',
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: Color(0xFFF8FAFC),
          ),
        ),
        const SizedBox(height: 8),

        if (!_usageAccess)
          _buildHonestState(
            icon: Icons.lock_outline,
            title: 'Usage Access Not Granted',
            subtitle:
                'Cannot read usage events without PACKAGE_USAGE_STATS permission. Tap "Grant Access" above.',
          )
        else if (!_hasQueriedWindows)
          _buildHonestState(
            icon: Icons.hourglass_empty,
            title: 'No Query Executed Yet',
            subtitle:
                'Specify a package and tap "Query Sessions" to extract real lifecycle windows.',
          )
        else if (_foregroundWindows.isEmpty)
          _buildHonestState(
            icon: Icons.event_busy,
            title: 'No Foreground Sessions Found',
            subtitle:
                'No ACTIVITY_RESUMED / ACTIVITY_STOPPED events found for ${_lifecyclePkgController.text} in the last 24 hours.',
          )
        else
          ..._foregroundWindows.asMap().entries.map((entry) {
            final index = entry.key + 1;
            final w = entry.value;
            final start = w['start'] as num? ?? 0;
            final end = w['end'] as num? ?? 0;
            final durationSec = ((end - start) / 1000).toStringAsFixed(1);
            final startDate = DateTime.fromMillisecondsSinceEpoch(start.toInt());
            final endDate = DateTime.fromMillisecondsSinceEpoch(end.toInt());

            return Card(
              color: const Color(0xFF151D30),
              margin: const EdgeInsets.symmetric(vertical: 4),
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: const Color(0xFF22D3EE).withValues(alpha: 0.2),
                  foregroundColor: const Color(0xFF22D3EE),
                  child: Text('$index'),
                ),
                title: Text(
                  'Duration: ${durationSec}s',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                subtitle: Text(
                  'From: ${_formatTime(startDate)}\nTo:   ${_formatTime(endDate)}',
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 12,
                    color: Color(0xFF94A3B8),
                  ),
                ),
                isThreeLine: true,
              ),
            );
          }),
      ],
    );
  }

  Widget _buildBaselineTab() {
    final granted = (_baselineData?['granted'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList() ??
        <String>[];
    final notGranted = (_baselineData?['notGranted'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList() ??
        <String>[];
    final targetSdk = _baselineData?['targetSdkVersion'] as num? ?? 0;
    final versionName = _baselineData?['versionName'] as String? ?? '';

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (_errorMessage != null) _buildErrorBanner(),

        // Baseline Query Card
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Permission Baseline Inspector',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF22D3EE),
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Queries PackageManager.getPackageInfo(GET_PERMISSIONS) and checks REQUESTED_PERMISSION_GRANTED flags for declared permissions.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: _baselinePkgController,
                  decoration: InputDecoration(
                    labelText: 'Package Name',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    filled: true,
                    fillColor: const Color(0xFF0B1020),
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  children: [
                    ActionChip(
                      label: const Text('PrivacyGuard'),
                      onPressed: () => _queryPermissionBaseline('com.privacyguard.app'),
                    ),
                    ActionChip(
                      label: const Text('Settings'),
                      onPressed: () => _queryPermissionBaseline('com.android.settings'),
                    ),
                    ActionChip(
                      label: const Text('Chrome'),
                      onPressed: () => _queryPermissionBaseline('com.android.chrome'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: const Color(0xFF0B1020),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    onPressed: _queryingBaseline
                        ? null
                        : () => _queryPermissionBaseline(_baselinePkgController.text),
                    icon: _queryingBaseline
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.shield_outlined),
                    label: Text(
                      _queryingBaseline ? 'Inspecting...' : 'Inspect Baseline',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 20),

        if (!_hasQueriedBaseline)
          _buildHonestState(
            icon: Icons.search,
            title: 'No Package Inspected Yet',
            subtitle:
                'Select or enter a package name above to inspect declared and granted permissions.',
          )
        else ...[
          // Package Metadata Card
          Card(
            color: const Color(0xFF151D30),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _baselinePkgController.text,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFFF8FAFC),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Target SDK: $targetSdk  •  Version: ${versionName.isNotEmpty ? versionName : "unknown"}',
                    style: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.green.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.greenAccent),
                        ),
                        child: Text(
                          '${granted.length} Granted',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Colors.greenAccent,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.orange.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.orangeAccent),
                        ),
                        child: Text(
                          '${notGranted.length} Revoked / Not Granted',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Colors.orangeAccent,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // Granted Permissions Group
          if (granted.isNotEmpty) ...[
            const Text(
              'Granted Permissions',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: Colors.greenAccent,
              ),
            ),
            const SizedBox(height: 8),
            ...granted.map((perm) => _buildPermissionTile(perm, isGranted: true)),
            const SizedBox(height: 16),
          ],

          // Revoked / Not Granted Group
          if (notGranted.isNotEmpty) ...[
            const Text(
              'Revoked / Not Granted Permissions',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.bold,
                color: Colors.orangeAccent,
              ),
            ),
            const SizedBox(height: 8),
            ...notGranted.map((perm) => _buildPermissionTile(perm, isGranted: false)),
          ],

          if (granted.isEmpty && notGranted.isEmpty)
            _buildHonestState(
              icon: Icons.check_circle_outline,
              title: 'No Permissions Declared',
              subtitle: 'This package declares 0 permissions in its AndroidManifest.',
            ),
        ],
      ],
    );
  }

  Widget _buildPermissionTile(String permission, {required bool isGranted}) {
    final simpleName = permission.replaceFirst('android.permission.', '');
    return Card(
      color: const Color(0xFF151D30),
      margin: const EdgeInsets.symmetric(vertical: 3),
      child: ListTile(
        dense: true,
        leading: Icon(
          isGranted ? Icons.check_circle : Icons.remove_circle_outline,
          color: isGranted ? Colors.greenAccent : Colors.orangeAccent,
          size: 20,
        ),
        title: Text(
          simpleName,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
        ),
        subtitle: Text(
          permission,
          style: const TextStyle(
            fontSize: 10,
            fontFamily: 'monospace',
            color: Color(0xFF94A3B8),
          ),
        ),
      ),
    );
  }

  Widget _buildCapabilitiesCard() {
    return Card(
      color: const Color(0xFF151D30),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Capabilities & Access',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Color(0xFF22D3EE),
              ),
            ),
            const SizedBox(height: 12),
            _buildStatusRow(
              'Usage Stats Access (P1)',
              _usageAccess ? 'Granted' : 'Not Granted',
              _usageAccess ? Colors.greenAccent : Colors.orangeAccent,
              action: !_usageAccess
                  ? OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFF22D3EE),
                        side: const BorderSide(color: Color(0xFF22D3EE)),
                        visualDensity: VisualDensity.compact,
                      ),
                      onPressed: _openUsageSettings,
                      child: const Text('Grant Access'),
                    )
                  : null,
            ),
            const Divider(color: Colors.white12),
            _buildStatusRow(
              'Shizuku Elevated Service (P3)',
              _shizukuRunning
                  ? (_shizukuPermission ? 'Authorized' : 'Permission needed')
                  : 'Not Running',
              _shizukuRunning
                  ? (_shizukuPermission ? Colors.greenAccent : Colors.orangeAccent)
                  : Colors.grey,
              action: _shizukuRunning && !_shizukuPermission
                  ? OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFF22D3EE),
                        side: const BorderSide(color: Color(0xFF22D3EE)),
                        visualDensity: VisualDensity.compact,
                      ),
                      onPressed: _requestShizukuPermission,
                      child: const Text('Authorize'),
                    )
                  : null,
            ),
            const Divider(color: Colors.white12),
            _buildStatusRow(
              'Device Owner (P7)',
              _deviceOwner ? 'Active' : 'Not Provisioned',
              _deviceOwner ? Colors.greenAccent : Colors.grey,
            ),
            const Divider(color: Colors.white12),
            _buildStatusRow(
              'Android Platform',
              'API $_apiLevel',
              const Color(0xFF94A3B8),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAccessEventsTab() {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (_errorMessage != null) _buildErrorBanner(),

        // Shizuku status banner if unavailable
        if (!_shizukuRunning || !_shizukuPermission)
          Container(
            margin: const EdgeInsets.only(bottom: 16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1E1B2E),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: _shizukuRunning ? Colors.orangeAccent : const Color(0xFF8B5CF6),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      _shizukuRunning ? Icons.warning_amber : Icons.info_outline,
                      color: _shizukuRunning ? Colors.orangeAccent : const Color(0xFF8B5CF6),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      _shizukuRunning
                          ? 'Shizuku Authorization Needed'
                          : 'Shizuku Service Not Running',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                        color: Color(0xFFF8FAFC),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  _shizukuRunning
                          ? 'PrivacyGuard needs permission in Shizuku Manager to read elevated AppOps access records. Please authorize PrivacyGuard.'
                          : 'AppOps runtime access history requires elevated privileges via Shizuku. Start Shizuku service via ADB on your computer or device.',
                  style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 12),
                if (_shizukuRunning && !_shizukuPermission)
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: const Color(0xFF0B1020),
                    ),
                    onPressed: _requestShizukuPermission,
                    icon: const Icon(Icons.security, size: 18),
                    label: const Text('Authorize PrivacyGuard'),
                  )
                else
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF22D3EE),
                      side: const BorderSide(color: Color(0xFF22D3EE)),
                    ),
                    onPressed: _refreshStatus,
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('Check Shizuku Status'),
                  ),
              ],
            ),
          ),

        // Query Card
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'AppOps Access Event Tracker',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF22D3EE),
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Inspect real-time and historical AppOps hardware operations (Camera, Microphone, Location, Contacts, SMS, Storage) through elevated Shizuku access.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _accessEventsPkgController,
                  decoration: InputDecoration(
                    labelText: 'Target Package Name',
                    labelStyle: const TextStyle(color: Color(0xFF94A3B8)),
                    hintText: 'e.g. com.android.chrome',
                    hintStyle: const TextStyle(color: Colors.white24),
                    filled: true,
                    fillColor: const Color(0xFF0B1020),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Colors.white12),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Color(0xFF22D3EE)),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: [
                    _buildQuickAppChip('Chrome', 'com.android.chrome', _queryAccessEvents),
                    _buildQuickAppChip('Google Play Services', 'com.google.android.gms', _queryAccessEvents),
                    _buildQuickAppChip('PrivacyGuard', 'com.privacyguard.app', _queryAccessEvents),
                    _buildQuickAppChip('Settings', 'com.android.settings', _queryAccessEvents),
                  ],
                ),
                const SizedBox(height: 12),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      const Text(
                        'Window: ',
                        style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        label: const Text('1 Hour'),
                        selected: _selectedSinceHours == 1,
                        onSelected: (val) {
                          if (val) setState(() => _selectedSinceHours = 1);
                        },
                      ),
                      const SizedBox(width: 6),
                      ChoiceChip(
                        label: const Text('24 Hours'),
                        selected: _selectedSinceHours == 24,
                        onSelected: (val) {
                          if (val) setState(() => _selectedSinceHours = 24);
                        },
                      ),
                      const SizedBox(width: 6),
                      ChoiceChip(
                        label: const Text('All History'),
                        selected: _selectedSinceHours == 0,
                        onSelected: (val) {
                          if (val) setState(() => _selectedSinceHours = 0);
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: const Color(0xFF0B1020),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    onPressed: _queryingAccessEvents
                        ? null
                        : () => _queryAccessEvents(),
                    child: _queryingAccessEvents
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text(
                            'Query AppOps Events',
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 20),

        if (!_hasQueriedAccessEvents)
          _buildHonestState(
            icon: Icons.privacy_tip_outlined,
            title: 'Ready to Query',
            subtitle: 'Choose an application above and tap "Query AppOps Events" to inspect elevated runtime hardware access.',
          )
        else if (_accessEvents.isEmpty)
          _buildHonestState(
            icon: Icons.search_off,
            title: 'No Access Events Logged',
            subtitle: 'No AppOps access events were recorded for "${_accessEventsPkgController.text}" in the selected timeframe.',
          )
        else ...[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 8.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${_accessEvents.length} Access Events Detected',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFFF8FAFC),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.greenAccent.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.greenAccent),
                  ),
                  child: const Text(
                    'Shizuku Live',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: Colors.greenAccent,
                    ),
                  ),
                ),
              ],
            ),
          ),
          ..._accessEvents.map(_buildAccessEventTile),
        ],
      ],
    );
  }

  Widget _buildAccessEventTile(Map<String, dynamic> event) {
    final op = event['op'] as String? ?? 'UNKNOWN';
    final accessTimeMs = (event['accessTimeMs'] as num?)?.toInt() ?? 0;
    final rejectTimeMs = (event['rejectTimeMs'] as num?)?.toInt() ?? 0;
    final durationMs = (event['durationMs'] as num?)?.toInt() ?? 0;
    final uidState = event['uidState'] as String? ?? 'unknown';
    final accessCount = (event['accessCount'] as num?)?.toInt() ?? 0;
    final rejectCount = (event['rejectCount'] as num?)?.toInt() ?? 0;
    final source = event['source'] as String? ?? 'history';

    final effectiveTimeMs = accessTimeMs > 0 ? accessTimeMs : rejectTimeMs;
    final formattedTime = effectiveTimeMs > 0
        ? _formatTime(DateTime.fromMillisecondsSinceEpoch(effectiveTimeMs))
        : 'Unknown time';

    IconData opIcon;
    Color opColor;

    if (op.contains('CAMERA')) {
      opIcon = Icons.camera_alt;
      opColor = Colors.redAccent;
    } else if (op.contains('AUDIO') || op.contains('RECORD')) {
      opIcon = Icons.mic;
      opColor = Colors.orangeAccent;
    } else if (op.contains('LOCATION')) {
      opIcon = Icons.location_on;
      opColor = Colors.cyanAccent;
    } else if (op.contains('CONTACTS')) {
      opIcon = Icons.contacts;
      opColor = Colors.purpleAccent;
    } else if (op.contains('SMS')) {
      opIcon = Icons.sms;
      opColor = Colors.yellowAccent;
    } else if (op.contains('STORAGE') || op.contains('MEDIA')) {
      opIcon = Icons.folder;
      opColor = Colors.tealAccent;
    } else if (op.contains('USAGE')) {
      opIcon = Icons.analytics;
      opColor = Colors.blueAccent;
    } else {
      opIcon = Icons.security;
      opColor = const Color(0xFF22D3EE);
    }

    final isRejectOnly = accessCount == 0 && rejectCount > 0;

    return Card(
      color: const Color(0xFF151D30),
      margin: const EdgeInsets.symmetric(vertical: 4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: BorderSide(
          color: isRejectOnly ? Colors.redAccent.withValues(alpha: 0.5) : Colors.white12,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: opColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(opIcon, color: opColor, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          op,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: Color(0xFFF8FAFC),
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: _getUidStateColor(uidState).withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(4),
                          border: Border.all(color: _getUidStateColor(uidState)),
                        ),
                        child: Text(
                          uidState.toUpperCase(),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: _getUidStateColor(uidState),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    formattedTime,
                    style: const TextStyle(
                      fontSize: 11,
                      fontFamily: 'monospace',
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 8,
                    children: [
                      if (durationMs > 0)
                        _buildInfoTag(Icons.timer_outlined, '${durationMs}ms'),
                      if (accessCount > 0)
                        _buildInfoTag(Icons.check, '$accessCount accesses'),
                      if (rejectCount > 0)
                        _buildInfoTag(Icons.block, '$rejectCount rejected', isError: true),
                      _buildInfoTag(Icons.source, source),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoTag(IconData icon, String text, {bool isError = false}) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 12, color: isError ? Colors.redAccent : const Color(0xFF94A3B8)),
        const SizedBox(width: 3),
        Text(
          text,
          style: TextStyle(
            fontSize: 11,
            color: isError ? Colors.redAccent : const Color(0xFF94A3B8),
          ),
        ),
      ],
    );
  }

  Color _getUidStateColor(String state) {
    switch (state.toLowerCase()) {
      case 'top':
      case 'fg':
        return Colors.greenAccent;
      case 'fgsvc':
        return Colors.cyanAccent;
      case 'bg':
        return Colors.orangeAccent;
      case 'cch':
        return Colors.purpleAccent;
      case 'pers':
        return Colors.blueAccent;
      default:
        return const Color(0xFF94A3B8);
    }
  }

  Widget _buildErrorBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.red.withValues(alpha: 0.15),
        border: Border.all(color: Colors.redAccent),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        _errorMessage!,
        style: const TextStyle(color: Colors.redAccent),
      ),
    );
  }

  Widget _buildScoringTab() {
    final scan = _scanResult?['scan'] as Map<dynamic, dynamic>?;
    final blockDecision = _scanResult?['blockDecision'] as Map<dynamic, dynamic>?;
    final windowsCount = _scanResult?['windowsCount'] as num? ?? 0;
    final eventsCount = _scanResult?['eventsCount'] as num? ?? 0;
    final baselineGrantedCount = _scanResult?['baselineGrantedCount'] as num? ?? 0;

    final riskScore = (scan?['riskScore'] as num?)?.toInt() ?? 0;
    final privacyHealth = (scan?['privacyHealth'] as num?)?.toInt() ?? 100;
    final confidence = (scan?['confidence'] as num?)?.toInt() ?? 0;
    final scanStatus = scan?['status'] as String? ?? 'PRELIMINARY';
    final mismatches = (scan?['mismatches'] as List<dynamic>?)
            ?.map((e) => Map<String, dynamic>.from(e as Map))
            .toList() ??
        <Map<String, dynamic>>[];
    final confidenceReport = scan?['confidenceReport'] as Map<dynamic, dynamic>?;
    final components = (confidenceReport?['components'] as List<dynamic>?)
            ?.map((e) => Map<String, dynamic>.from(e as Map))
            .toList() ??
        <Map<String, dynamic>>[];

    final verdict = blockDecision?['verdict'] as String? ?? 'UNKNOWN';
    final explanation = blockDecision?['explanation'] as String? ?? '';

    Color riskColor;
    if (riskScore > 90) {
      riskColor = Colors.redAccent;
    } else if (riskScore > 70) {
      riskColor = Colors.orangeAccent;
    } else if (riskScore > 30) {
      riskColor = Colors.amberAccent;
    } else {
      riskColor = Colors.greenAccent;
    }

    Color verdictColor;
    if (verdict == 'BLOCKED') {
      verdictColor = Colors.redAccent;
    } else if (verdict == 'WARN_ONLY') {
      verdictColor = Colors.orangeAccent;
    } else if (verdict == 'NOT_BLOCKABLE') {
      verdictColor = const Color(0xFF22D3EE);
    } else {
      verdictColor = Colors.greenAccent;
    }

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (_errorMessage != null) _buildErrorBanner(),

        // Audit query card
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Risk Scoring & Policy Engine',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF22D3EE),
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Evaluate runtime hardware access against foreground lifecycle windows and permission baseline to produce deterministic risk scores and block decisions.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _scoringPkgController,
                  style: const TextStyle(fontSize: 14, color: Color(0xFFF8FAFC)),
                  decoration: const InputDecoration(
                    labelText: 'Target Package Name',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: [
                    _buildQuickAppChip('Chrome', 'com.android.chrome', (pkg) => _runAuditScan(pkg)),
                    _buildQuickAppChip('Google Play Services', 'com.google.android.gms', (pkg) => _runAuditScan(pkg)),
                    _buildQuickAppChip('PrivacyGuard', 'com.privacyguard.app', (pkg) => _runAuditScan(pkg)),
                    _buildQuickAppChip('Settings', 'com.android.settings', (pkg) => _runAuditScan(pkg)),
                  ],
                ),
                const SizedBox(height: 12),
                // Policy simulation options
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0B1020),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Policy Simulation Context',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF94A3B8)),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          ChoiceChip(
                            label: const Text('Adult (25)'),
                            selected: _userAge >= 18,
                            onSelected: (val) {
                              if (val) setState(() => _userAge = 25);
                            },
                          ),
                          ChoiceChip(
                            label: const Text('Child (15)'),
                            selected: _userAge < 18,
                            onSelected: (val) {
                              if (val) setState(() => _userAge = 15);
                            },
                          ),
                          if (_userAge >= 18)
                            FilterChip(
                              label: const Text('Hard Blocking'),
                              selected: _hardBlockingEnabled,
                              onSelected: (val) => setState(() => _hardBlockingEnabled = val),
                            )
                          else
                            FilterChip(
                              label: const Text('Parent Linked'),
                              selected: _hasParentLink,
                              onSelected: (val) => setState(() => _hasParentLink = val),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: const Color(0xFF0B1020),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    onPressed: _runningScan ? null : () => _runAuditScan(),
                    child: _runningScan
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text(
                            'Run Risk & Scoring Audit',
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 20),

        if (!_hasScanned)
          _buildHonestState(
            icon: Icons.analytics_outlined,
            title: 'Ready to Audit',
            subtitle: 'Choose an application above and tap "Run Risk & Scoring Audit" to evaluate risk score, confidence, and policy verdicts.',
          )
        else ...[
          // Metrics Cards Row
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF151D30),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: riskColor.withValues(alpha: 0.3)),
                  ),
                  child: Column(
                    children: [
                      Text(
                        '$riskScore',
                        style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: riskColor),
                      ),
                      const SizedBox(height: 4),
                      const Text('Risk Score', style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8))),
                      const SizedBox(height: 4),
                      Text(
                        'Health: $privacyHealth',
                        style: const TextStyle(fontSize: 11, color: Colors.greenAccent),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF151D30),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: Column(
                    children: [
                      Text(
                        '$confidence%',
                        style: const TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: Color(0xFF22D3EE)),
                      ),
                      const SizedBox(height: 4),
                      const Text('Confidence', style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8))),
                      const SizedBox(height: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: scanStatus == 'COMPLETE'
                              ? Colors.greenAccent.withValues(alpha: 0.2)
                              : Colors.amberAccent.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          scanStatus,
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: scanStatus == 'COMPLETE' ? Colors.greenAccent : Colors.amberAccent,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          // Block Decision Card
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF151D30),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: verdictColor.withValues(alpha: 0.5)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Block Policy Verdict',
                      style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFFF8FAFC)),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: verdictColor.withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: verdictColor),
                      ),
                      child: Text(
                        verdict,
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: verdictColor),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  explanation,
                  style: const TextStyle(fontSize: 12, color: Color(0xFFCBD5E1)),
                ),
              ],
            ),
          ),

          const SizedBox(height: 16),

          // Confidence Components Breakdown
          const Text(
            'Confidence Quality Factors',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFFF8FAFC)),
          ),
          const SizedBox(height: 8),
          ...components.map((comp) {
            final name = comp['name'] as String? ?? '';
            final scoreContrib = (comp['scoreContribution'] as num?)?.toInt() ?? 0;
            final detail = comp['detail'] as String? ?? '';

            return Card(
              color: const Color(0xFF151D30),
              margin: const EdgeInsets.symmetric(vertical: 3),
              child: ListTile(
                dense: true,
                title: Text(name, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                subtitle: Text(detail, style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8))),
                trailing: Text(
                  '+$scoreContrib%',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF22D3EE)),
                ),
              ),
            );
          }),

          const SizedBox(height: 16),

          // Top 3 Mismatches
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Top Mismatches (${mismatches.length})',
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFFF8FAFC)),
              ),
              if (mismatches.isNotEmpty)
                const Text(
                  'Ranked by severity',
                  style: TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                ),
            ],
          ),
          const SizedBox(height: 8),

          if (mismatches.isEmpty)
            _buildHonestState(
              icon: Icons.verified_user,
              title: 'Zero Mismatches Detected',
              subtitle: 'All observed runtime accesses align with declared foreground lifecycle boundaries.',
            )
          else
            ...mismatches.asMap().entries.map((entry) {
              final rank = entry.key + 1;
              final m = entry.value;
              final op = m['permissionOrOp'] as String? ?? '';
              final expected = m['expectedState'] as String? ?? '';
              final observed = m['observedState'] as String? ?? '';
              final count = m['evidenceCount'] as num? ?? 0;
              final severity = m['severityScore'] as num? ?? 0;
              final detail = m['detail'] as String? ?? '';

              return Card(
                color: const Color(0xFF151D30),
                margin: const EdgeInsets.symmetric(vertical: 4),
                shape: RoundedRectangleBorder(
                  side: const BorderSide(color: Colors.redAccent, width: 0.5),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              CircleAvatar(
                                radius: 10,
                                backgroundColor: Colors.redAccent.withValues(alpha: 0.2),
                                child: Text('$rank', style: const TextStyle(fontSize: 10, color: Colors.redAccent)),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                op,
                                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Colors.white),
                              ),
                            ],
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: Colors.redAccent.withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              'Severity $severity',
                              style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.redAccent),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(detail, style: const TextStyle(fontSize: 12, color: Color(0xFFCBD5E1))),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Text(
                            'Expected: $expected',
                            style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                          ),
                          const SizedBox(width: 12),
                          Text(
                            'Observed: $observed ($count count)',
                            style: const TextStyle(fontSize: 10, color: Colors.orangeAccent),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            }),

          const SizedBox(height: 16),

          // Ground Truth Audit Statistics Card
          Card(
            color: const Color(0xFF151D30),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Ground Truth Observation Footprint', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF94A3B8))),
                  const SizedBox(height: 6),
                  _buildStatusRow('Foreground Sessions (P1)', '$windowsCount windows', const Color(0xFFF8FAFC)),
                  _buildStatusRow('AppOps Access Events (P3)', '$eventsCount events', const Color(0xFFF8FAFC)),
                  _buildStatusRow('Baseline Granted Permissions (P2)', '$baselineGrantedCount permissions', const Color(0xFFF8FAFC)),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildStatusRow(
    String label,
    String value,
    Color valueColor, {
    Widget? action,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: valueColor,
                  ),
                ),
              ],
            ),
          ),
          ?action,
        ],
      ),
    );
  }

  Widget _buildHonestState({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: const Color(0xFF151D30),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        children: [
          Icon(icon, size: 40, color: const Color(0xFF94A3B8)),
          const SizedBox(height: 12),
          Text(
            title,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: Color(0xFFF8FAFC),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickAppChip(String label, String pkg, Function(String) onSelect) {
    return ActionChip(
      label: Text(label, style: const TextStyle(fontSize: 11)),
      backgroundColor: const Color(0xFF0B1020),
      side: const BorderSide(color: Colors.white12),
      onPressed: () => onSelect(pkg),
    );
  }

  String _formatTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}:${dt.second.toString().padLeft(2, '0')}';
  }

  // ==========================================
  // P5 History & Timeline Tab
  // ==========================================

  Widget _buildHistoryTab() {
    final isScheduled = _auditStatus?['isPeriodicScheduled'] as bool? ?? false;
    final lastAuditMs = (_auditStatus?['lastAuditCompletedMs'] as num?)?.toInt() ?? 0;
    final totalEvents = (_auditStatus?['totalEventsCount'] as num?)?.toInt() ?? 0;
    final totalSessions = (_auditStatus?['totalSessionsCount'] as num?)?.toInt() ?? 0;
    final totalScores = (_auditStatus?['totalScoresCount'] as num?)?.toInt() ?? 0;

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        if (_auditBannerMessage != null) ...[
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF22D3EE).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFF22D3EE).withValues(alpha: 0.4)),
            ),
            child: Row(
              children: [
                const Icon(Icons.info_outline, color: Color(0xFF22D3EE), size: 18),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _auditBannerMessage!,
                    style: const TextStyle(fontSize: 13, color: Color(0xFFF8FAFC)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        // 1. Worker Status Card
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.sync_rounded, color: Color(0xFF22D3EE), size: 22),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Background Audit Worker (WorkManager & Room)',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF22D3EE),
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: isScheduled
                            ? const Color(0xFF10B981).withValues(alpha: 0.2)
                            : const Color(0xFF64748B).withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: isScheduled ? const Color(0xFF10B981) : const Color(0xFF64748B),
                        ),
                      ),
                      child: Text(
                        isScheduled ? 'SCHEDULED (15m)' : 'NOT SCHEDULED',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: isScheduled ? const Color(0xFF10B981) : const Color(0xFF94A3B8),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'WorkManager periodic job queries AppOps and UsageEvents in background, deduplicating access events into Room SQLite DB (FR-H1, FR-H2, FR-H3).',
                  style: TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                ),
                const SizedBox(height: 14),

                // Stats Row
                Row(
                  children: [
                    Expanded(
                      child: _buildMetricTile(
                        label: 'Room Events',
                        value: '$totalEvents',
                        color: const Color(0xFF22D3EE),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricTile(
                        label: 'Room Sessions',
                        value: '$totalSessions',
                        color: const Color(0xFF8B5CF6),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildMetricTile(
                        label: 'Risk Scores',
                        value: '$totalScores',
                        color: const Color(0xFF38BDF8),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  'Last Audit Watermark: ${lastAuditMs > 0 ? _formatTime(DateTime.fromMillisecondsSinceEpoch(lastAuditMs)) : "None"}',
                  style: const TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                ),
                const SizedBox(height: 14),

                // Actions
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF22D3EE),
                          foregroundColor: const Color(0xFF0B1020),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        icon: _triggeringAudit
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Color(0xFF0B1020),
                                ),
                              )
                            : const Icon(Icons.play_arrow_rounded, size: 18),
                        label: Text(
                          _triggeringAudit ? 'Auditing...' : 'Run Audit Now',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        onPressed: _triggeringAudit ? null : _triggerImmediateAudit,
                      ),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: isScheduled ? const Color(0xFFEF4444) : const Color(0xFF10B981),
                        side: BorderSide(
                          color: isScheduled ? const Color(0xFFEF4444) : const Color(0xFF10B981),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      icon: Icon(
                        isScheduled ? Icons.stop_circle_outlined : Icons.schedule,
                        size: 16,
                      ),
                      label: Text(isScheduled ? 'Cancel Worker' : 'Schedule 15m'),
                      onPressed: _togglePeriodicSchedule,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        // 2. Package Selector
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Package History & Timelines',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFFF8FAFC),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _historyPkgController,
                  decoration: InputDecoration(
                    labelText: 'Target Package Name',
                    labelStyle: const TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                    filled: true,
                    fillColor: const Color(0xFF0B1020),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Colors.white12),
                    ),
                    suffixIcon: IconButton(
                      icon: _queryingHistory
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.search, color: Color(0xFF22D3EE)),
                      onPressed: _queryHistoryAndTimeline,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: [
                    _buildQuickAppChip('Chrome', 'com.android.chrome', (pkg) {
                      _historyPkgController.text = pkg;
                      _queryHistoryAndTimeline();
                    }),
                    _buildQuickAppChip('Settings', 'com.android.settings', (pkg) {
                      _historyPkgController.text = pkg;
                      _queryHistoryAndTimeline();
                    }),
                    _buildQuickAppChip('PrivacyGuard', 'com.privacyguard.app', (pkg) {
                      _historyPkgController.text = pkg;
                      _queryHistoryAndTimeline();
                    }),
                  ],
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        // 3. Risk Score Trends (runtime_scores)
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Text(
                        'Risk Score Evolution (Room: runtime_scores)',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF22D3EE),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${_scoreHistory.length} recorded',
                      style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                    ),
                  ],
                ),

                const SizedBox(height: 12),
                if (_scoreHistory.isEmpty)
                  _buildHonestState(
                    icon: Icons.history_toggle_off,
                    title: 'No Risk Scores in Room DB',
                    subtitle: 'Trigger an audit cycle or run a scan in P4 to persist risk score history.',
                  )
                else
                  ..._scoreHistory.map((score) => _buildScoreHistoryItem(score)),
              ],
            ),
          ),
        ),

        const SizedBox(height: 16),

        // 4. AppOps Access Timeline (access_events)
        Card(
          color: const Color(0xFF151D30),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Text(
                        'Hardware Access Timeline (Room: access_events)',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF8B5CF6),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${_timelineEvents.length} events',
                      style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                    ),
                  ],
                ),

                const SizedBox(height: 12),
                if (_timelineEvents.isEmpty)
                  _buildHonestState(
                    icon: Icons.access_time_outlined,
                    title: 'No Access Events Logged',
                    subtitle: 'No AppOps access events recorded in Room database for this package.',
                  )
                else
                  ..._timelineEvents.map((evt) => _buildTimelineItem(evt)),
              ],
            ),
          ),
        ),

      ],
    );
  }

  Widget _buildMetricTile({
    required String label,
    required String value,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1020),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildScoreHistoryItem(Map<String, dynamic> score) {
    final risk = (score['runtimeRisk'] as num?)?.toInt() ?? 0;
    final health = (100 - risk).clamp(0, 100);
    final computedAtMs = (score['computedAtMs'] as num?)?.toInt() ?? 0;
    final rawPenalty = (score['rawPenalty'] as num?)?.toInt() ?? 0;

    final Color riskColor = risk <= 10
        ? const Color(0xFF10B981)
        : (risk < 90 ? const Color(0xFFF59E0B) : const Color(0xFFEF4444));

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1020),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        children: [
          // Risk Score Badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: riskColor.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: riskColor.withValues(alpha: 0.5)),
            ),
            child: Column(
              children: [
                Text(
                  '$risk',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: riskColor,
                  ),
                ),
                Text(
                  'Risk',
                  style: TextStyle(fontSize: 9, color: riskColor),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Privacy Health: $health / 100',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFFF8FAFC),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  computedAtMs > 0
                      ? _formatTime(DateTime.fromMillisecondsSinceEpoch(computedAtMs))
                      : 'Unknown time',
                  style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                ),
              ],
            ),
          ),
          Text(
            'Raw: $rawPenalty',
            style: const TextStyle(fontSize: 11, color: Color(0xFF64748B)),
          ),
        ],
      ),
    );
  }

  Widget _buildTimelineItem(Map<String, dynamic> evt) {
    final op = evt['op'] as String? ?? 'UNKNOWN_OP';
    final state = evt['state'] as String? ?? 'BACKGROUND';
    final accessTimeMs = (evt['accessTimeMs'] as num?)?.toInt() ?? 0;
    final durationMs = (evt['durationMs'] as num?)?.toInt() ?? 0;
    final isFg = state == 'FOREGROUND';

    final IconData icon = _getHardwareIcon(op);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1020),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: isFg
              ? const Color(0xFF22D3EE).withValues(alpha: 0.3)
              : const Color(0xFF8B5CF6).withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        children: [
          Icon(icon, size: 20, color: isFg ? const Color(0xFF22D3EE) : const Color(0xFF8B5CF6)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  op,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFFF8FAFC),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  accessTimeMs > 0
                      ? _formatTime(DateTime.fromMillisecondsSinceEpoch(accessTimeMs))
                      : 'Time unknown',
                  style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                ),
              ],
            ),
          ),
          if (durationMs > 0)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Text(
                '${durationMs}ms',
                style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
              ),
            ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
            decoration: BoxDecoration(
              color: isFg
                  ? const Color(0xFF22D3EE).withValues(alpha: 0.15)
                  : const Color(0xFF8B5CF6).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(4),
              border: Border.all(
                color: isFg ? const Color(0xFF22D3EE) : const Color(0xFF8B5CF6),
                width: 0.8,
              ),
            ),
            child: Text(
              state,
              style: TextStyle(
                fontSize: 9,
                fontWeight: FontWeight.bold,
                color: isFg ? const Color(0xFF22D3EE) : const Color(0xFF8B5CF6),
              ),
            ),
          ),
        ],
      ),

    );
  }

  IconData _getHardwareIcon(String op) {
    if (op.contains('CAMERA')) return Icons.camera_alt_outlined;
    if (op.contains('AUDIO') || op.contains('RECORD')) return Icons.mic_outlined;
    if (op.contains('LOCATION')) return Icons.location_on_outlined;
    if (op.contains('STORAGE') || op.contains('MEDIA')) return Icons.folder_outlined;
    return Icons.privacy_tip_outlined;
  }
}


