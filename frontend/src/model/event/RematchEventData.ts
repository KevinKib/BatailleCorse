export default interface RematchEventData {
  status: 'PENDING' | 'STARTED';
  requestedBy: { id: string };
}
