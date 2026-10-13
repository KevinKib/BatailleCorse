import './commands';
import { disconnectAllScriptedPlayers } from './stompPlayer';

// No scripted player outlives its test, whether the test passed or failed.
afterEach(() => cy.wrap(disconnectAllScriptedPlayers(), { log: false, timeout: 10000 }));
