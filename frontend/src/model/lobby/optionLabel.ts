import { format } from '../../locales/format';
import type { Messages, OptionTexts } from '../../locales/Messages';
import type { OptionDescriptor } from './LobbyView';

/** The texts of an option group: `options.bullshit.claimMode` leads to `messages.options.bullshit.claimMode`. */
function textsOf(messages: Messages, descriptor: OptionDescriptor): OptionTexts | undefined {
  const [root, game, key] = descriptor.labelKey.split('.');
  if (root !== 'options') return undefined;
  const group = (messages.options as unknown as Record<string, Record<string, unknown>>)[game];
  return group?.[key] as OptionTexts | undefined;
}

/** Title of an option group; falls back to the option key. */
export function optionGroupLabel(messages: Messages, descriptor: OptionDescriptor): string {
  return textsOf(messages, descriptor)?.label ?? descriptor.key;
}

/**
 * Text of one value of an option; falls back to the raw value. `options` is the whole option map: a text may
 * quote another option (the rank mode shows the range of the deck in play, `{range}`).
 */
export function optionLabel(
  messages: Messages,
  descriptor: OptionDescriptor,
  value: string,
  options: Record<string, string>,
): string {
  const template = textsOf(messages, descriptor)?.[value];
  if (template === undefined || value === 'label') return value;
  const [, game] = descriptor.labelKey.split('.');
  const vars = (messages.options as unknown as Record<string, { vars?: Record<string, Record<string, Record<string, string>>> }>)[game]?.vars ?? {};
  const resolved: Record<string, string> = {};
  for (const [name, byOption] of Object.entries(vars)) {
    for (const [optionKey, byValue] of Object.entries(byOption)) {
      resolved[name] = byValue[options[optionKey]] ?? '';
    }
  }
  return format(template, resolved).replace(/\s*\(\)/, '');
}
