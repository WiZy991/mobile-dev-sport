; Inno Setup — FitnessClub Agent
; Сборка: build_installer.ps1 (из корня club_agent)

#define MyAppName "FitnessClub Agent"
#define MyAppPublisher "FitnessClub"
#define MyAppExeName "FitnessClubAgent.exe"
#define MyAppVersion "1.0.0"

[Setup]
AppId={{A7B3C9D1-4E2F-5A6B-8C9D-0E1F2A3B4C5D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\FitnessClubAgent
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=output
OutputBaseFilename=FitnessClubAgent-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Shortcut on desktop"; GroupDescription: "Additional:"
Name: "firewall"; Description: "Allow inbound TCP 8765 (C01 / turnstile)"; GroupDescription: "Additional:"

[Files]
Source: "staging\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "staging\config\agent_config.json"; DestDir: "{app}\config"; Flags: onlyifdoesntexist ignoreversion
Source: "uninstall.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon; WorkingDir: "{app}"

[Run]
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""FitnessClub Agent C01"" dir=in action=allow protocol=TCP localport=8765"; Flags: runhidden; Tasks: firewall; StatusMsg: "Firewall..."
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\config"
