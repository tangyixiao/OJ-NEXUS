; OJ NEXUS Windows installer — Inno Setup 6 script.
; Compiled by windows/scripts/build-installer.ps1 with /DSourceRoot=<staging root>
; and /DMyAppVersion=<semver>. The staging root is the package layout produced by
; windows/scripts/package.ps1: cli\ (ojnexus.exe) and desktop\ (WPF app).

#define MyAppName "OJ NEXUS"
#define MyAppPublisher "OJ NEXUS"
#ifndef MyAppVersion
#define MyAppVersion "0.0.0"
#endif
#ifndef SourceRoot
#define SourceRoot "..\\artifacts\\self-contained\\staging"
#endif

[Setup]
AppId={{8F4A1C6E-2B7D-4E5A-9C08-OJNEXUSWIN64}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\OJNEXUS
PrivilegesRequired=lowest
OutputDir=installer-output
OutputBaseFilename=OJ-NEXUS-Setup-v{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
WizardStyle=modern
Uninstallable=yes
CreateUninstallRegKey=yes

[Files]
Source: "{#SourceRoot}\cli\*"; DestDir: "{app}\cli"; Flags: recursesubdirs createallsubdirs ignoreversion
Source: "{#SourceRoot}\desktop\*"; DestDir: "{app}\desktop"; Flags: recursesubdirs createallsubdirs ignoreversion
Source: "{#SourceRoot}\README.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceRoot}\SHA256SUMS.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\OJ NEXUS"; Filename: "{app}\desktop\OjNexus.Windows.Desktop.exe"
Name: "{autoprograms}\OJ NEXUS CLI"; Filename: "{app}\cli\ojnexus.exe"
Name: "{autodesktop}\OJ NEXUS"; Filename: "{app}\desktop\OjNexus.Windows.Desktop.exe"

[Run]
Filename: "{app}\desktop\OjNexus.Windows.Desktop.exe"; Description: "{cm:LaunchProgram,OJ NEXUS}"; Flags: nowait postinstall skipifsilent
