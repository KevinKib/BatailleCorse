import { spawn, type ChildProcess } from 'node:child_process';

/**
 * Keeps a Windows machine out of (Modern) Standby while `cypress run` is running.
 *
 * Standby on idle froze every hung run we traced: the run stops printing within seconds of the "Idle Timeout"
 * standby entry in the System event log and never resumes after wake-up, so it only ends when killed. A run does
 * not count as user activity, so a long spec loop on an idle laptop always ends up there.
 *
 * A PowerShell child holds SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED)
 * until the run ends; it also exits on its own once this process is gone, so a killed run leaves nothing behind.
 * Elsewhere than on Windows it does nothing.
 */
export function keepAwakeDuringRun(on: Cypress.PluginEvents): void {
  if (process.platform !== 'win32') return;
  let holder: ChildProcess | null = null;
  on('before:run', () => {
    // 2147483651 = 0x80000003 = ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED.
    const script = [
      `$k = Add-Type -Name Power -Namespace KeepAwake -PassThru -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags);'`,
      `[void]$k::SetThreadExecutionState([uint32]2147483651)`,
      `while (Get-Process -Id ${process.pid} -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 5 }`,
    ].join('; ');
    holder = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], {
      stdio: 'ignore',
      windowsHide: true,
    });
    holder.on('error', () => { holder = null; });
  });
  on('after:run', () => {
    holder?.kill();
    holder = null;
  });
}
