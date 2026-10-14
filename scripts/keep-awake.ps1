# Keeps Windows out of (Modern) Standby until the sentinel file is deleted (or MaxHours elapse).
#
#   powershell -NoProfile -File scripts/keep-awake.ps1 -Sentinel <file> [-MaxHours 6]
#
# Held by scripts/e2e-loop.sh for the whole loop of runs, so the gap between two `cypress run` processes is
# covered too (the 03:56 "Idle Timeout" standby of the 2026-10-14 investigation fell in such a gap).
# SetThreadExecutionState does not stop a closed lid: keep the lid open and the laptop plugged in.
param(
  [Parameter(Mandatory = $true)][string]$Sentinel,
  [double]$MaxHours = 6
)

$k = Add-Type -Name Power -Namespace KeepAwake -PassThru -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags);'
# 2147483651 = 0x80000003 = ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED.
[void]$k::SetThreadExecutionState([uint32]2147483651)

$deadline = (Get-Date).AddHours($MaxHours)
while ((Test-Path -LiteralPath $Sentinel) -and ((Get-Date) -lt $deadline)) {
  Start-Sleep -Seconds 2
}
