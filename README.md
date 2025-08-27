# Capacitor NFC Plugin (base on @exxili/capacitor-nfc)

A Capacitor plugin for reading and writing NFC tags on iOS and Android devices. This plugin allows you to:

- Read NDEF messages from NFC tags.
- Write NDEF messages to NFC tags.

**Note**: NFC functionality is only available on compatible iOS devices running iOS 13.0 or later.

## Table of Contents

- [Installation](#installation)
- [iOS Setup](#ios-setup)
- [Android Setup](#android-setup)
- [Usage](#usage)
  - [Reading NFC Tags](#reading-nfc-tags)
  - [Writing NFC Tags](#writing-nfc-tags)
- [API](#api)
  - [Methods](#methods)
    - [`isSupported()`](#issupported)
    - [`startScan()`](#startscan)
    - [`writeNDEF(options)`](#writendefoptions-ndefwriteoptionst-extends-string--number--uint8array--string)
    - [`cancelWriteAndroid`](#cancelwriteandroid)
  - [Listeners](#listeners)
    - [`onRead(listener)`](#onreadlistener-data-ndefmessagestransformable--void)
    - [`onError('listener)`](#onerrorlistener-error-nfcerror--void)
    - [`onWrite(listener)`](#onwritelistener---void)
  - [Interfaces](#interfaces)
    - [`NDEFWriteOptions`](#ndefwriteoptions)
    - [`NDEFWriteOptions`](#ndefmessagestransformable)
    - [`NDEFMessages`](#ndefmessages)
    - [`NDEFMessage`](#ndefmessage)
    - [`NDEFRecord`](#ndefrecord)
    - [`NFCError`](#nfcerror)
- [Integration into a Capacitor App](#integration-into-a-capacitor-app)
- [Example](#example)
- [License](#license)

## Installation

Install the plugin using npm:

```bash
npm install @exxili/capacitor-nfc
npx cap sync
```

## iOS Setup

To use NFC functionality on iOS, you need to perform some additional setup steps.

### 1. Enable NFC Capability

In Xcode:

1. Open your project (`.xcworkspace` file) in Xcode.
2. Select your project in the Project Navigator.
3. Select your app target.
4. Go to the **Signing & Capabilities** tab.
5. Click the `+ Capability` button.
6. Add **Near Field Communication Tag Reading**.

### 2. Add Usage Description

Add the `NFCReaderUsageDescription` key to your `Info.plist` file to explain why your app needs access to NFC.

In your `Info.plist` file (usually located at `ios/App/App/Info.plist`), add:

```xml
<key>NFCReaderUsageDescription</key>
<string>This app requires access to NFC to read and write NFC tags.</string>
```

Replace the description with a message that explains why your app needs NFC access.

## Android Setup

Add the following to your `AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

### notice

I modify Android impl make it can read "http/https" records.
And use TextEncoder to decode base64 result

## Usage

Import the plugin into your code:

```typescript
import { NFC } from '@exxili/capacitor-nfc';
```

### Reading NFC Tags

To read NFC tags, you need to listen for `nfcTag` events. On iOS, you must also start the NFC scanning session using `startScan()`.

```typescript
import {NFC, NDEFMessagesTransformable, NFCError} from '@exxili/capacitor-nfc';

// Start NFC scanning
NFC.startScan().catch((error) => {
  console.error('Error starting NFC scan:', error);
});

// Listen for NFC tag detection
NFC.onRead((data: NDEFMessagesTransformable) => {
  console.log('Received NFC tag:', data.string());
});

// Handle NFC errors
NFC.onError('nfcError', (error: NFCError) => {
  console.error('NFC Error:', error);
});
```

### Writing NFC Tags

To write NDEF messages to NFC tags, use the `writeNDEF` method and listen for `onWrite` events.

```typescript
import { NFC, NDEFWriteOptions, NFCError } from '@exxili/capacitor-nfc';

const message: NDEFWriteOptions = {
  records: [
    {
      type: 'T', // Text record type
      payload: 'Hello, NFC!',
    },
  ],
};

// Write NDEF message to NFC tag
NFC.writeNDEF(message)
  .then(() => {
    console.log('Write initiated');
  })
  .catch((error) => {
    console.error('Error writing to NFC tag:', error);
  });

// Listen for write success
NFC.onWrite(() => {
  console.log('NDEF message written successfully.');
});

// Handle NFC errors
NFC.onError((error: NFCError) => {
  console.error('NFC Error:', error);
});
```

## API

### Methods

#### `isSupported()`

Returns if NFC is supported on the scanning device.

**Returns**: `Promise<{ supported: boolean }>`

#### `startScan()`

Starts the NFC scanning session on ***iOS only***. Android devices are always in reading mode, so setting up the `nfcTag` listener is sufficient to handle tag reads on Android.

**Returns**: `Promise<void>`

```typescript
NFC.startScan()
  .then(() => {
    // Scanning started
  })
  .catch((error) => {
    console.error('Error starting NFC scan:', error);
  });
```

#### `writeNDEF(options: NDEFWriteOptions<T extends string | number[] | Uint8Array = string)`

Writes an NDEF message to an NFC tag.

Payload may be provided as a string, `Uint8Array`, or an array of numbers. The plugin will automatically convert the payload to a byte array for storage on the NFC tag.

Android use: since Android has no default UI for reading and writing NFC tags, it is recommended that you add a UI indicator to your application when calling `writeNDEF` and remove it in the `nfcWriteSuccess` listener callback and the `nfcError` listener callback. This will prevent accidental writes to tags that your users intended to read from.

**Parameters**:

- `options: NDEFWriteOptions<T extends string | number[] | Uint8Array = string>` - The NDEF message to write.

**Returns**: `Promise<void>`

```typescript
NFC.writeNDEF(options)
  .then(() => {
    // Write initiated
  })
  .catch((error) => {
    console.error('Error writing NDEF message:', error);
  });
```

#### `cancelWriteAndroid()`

Cancels an Android NFC write operation. Android does not have a native UI for NFC tag writing, so this method allows developers to hook up a custom UI to cancel an in-progress scan.

### Listeners

#### `onRead(listener: (data: NDEFMessagesTransformable) => void)`

Adds a listener for NFC tag detection events. Returns type `NDEFMessagesTransformable`, which returns the following methods to provide the payload:

- `string()`: Returns `NDEFMessages<string>`, where all payloads are strings.
- `base64()`: Returns `NDEFMessages<string>`, where all payloads are the base64-encoded payloads read from the NFC tag.
- `uint8Array()`: Returns `NDEFMessages<Uint8Array>`, where all payloads are the `Uint8Array` bytes from the NFC tag.
- `numberArray()`: Returns `NDEFMessages<number[]>`, where all payloads bytes from the NFC tag represented as a `number[]`.

**Parameters**:

- `listener: (data: NDEFMessagesTransformable) => void` - The function to call when an NFC tag is detected.

**Returns**: `void`

```typescript
NFC.onRead((data: NDEFMessages) => {
  console.log('Received NFC tag:', data);
});
```

#### `onError(listener: (error: NFCError) => void)`

Adds a listener for NFC error events.

**Parameters**:

- `listener: (error: NFCError) => void` - The function to call when an NFC error occurs.

**Returns**: `PluginListenerHandle`

```typescript
NFC.onError((error: NFCError) => {
  console.error('NFC Error:', error);
});
```

#### `onWrite(listener: () => void)`

Adds a listener for NFC write success events.

**Parameters**:

- `listener: () => void` - The function to call when an NDEF message has been written successfully.

**Returns**: `PluginListenerHandle`

```typescript
NFC.onWrite('nfcWriteSuccess', () => {
  console.log('NDEF message written successfully.');
});
```

### Interfaces

#### `NDEFWriteOptions`

Options for writing an NDEF message.

```typescript
interface NDEFWriteOptions<T extends string | number[] | Uint8Array = string> {
  records: NDEFRecord<T>[];
}
```

#### `NDEFMessagesTransformable`

Returned by `onRead` and includes the following methods to provide the payload:

- `string()`: Returns `NDEFMessages<string>`, where all payloads are strings.
- `base64()`: Returns `NDEFMessages<string>`, where all payloads are the base64-encoded payloads read from the NFC tag.
- `uint8Array()`: Returns `NDEFMessages<Uint8Array>`, where all payloads are the `Uint8Array` bytes from the NFC tag.
- `numberArray()`: Returns `NDEFMessages<number[]>`, where all payloads bytes from the NFC tag represented as a `number[]`.

```typescript
interface NDEFMessagesTransformable {
  base64: ()=> NDEFMessages;
  uint8Array: ()=> NDEFMessages<Uint8Array>;
  string: ()=> NDEFMessages;
  numberArray: ()=> NDEFMessages<number[]>;
}
```

#### `NDEFMessages`

Data received from an NFC tag.

```typescript
interface NDEFMessages {
  messages: NDEFMessage[];
}
```

#### `NDEFMessage`

An NDEF message consisting of one or more records.

```typescript
interface NDEFMessage {
  records: NDEFRecord[];
}
```

#### `NDEFRecord`

An NDEF record. `payload` is, by default, an array of bytes representing the data; this is how an `NDEFRecord` is read from an NFC tag. You can choose to provide an `NDEFRecord` as a string a `Uint8Array` also.

```typescript
interface NDEFRecord<T = number[]> {
  /**
   * The type of the record.
   */
  type: string;

  /**
   * The payload of the record.
   */
  payload: T;
}
````

#### `NFCError`

An NFC error.

```typescript
interface NFCError {
  /**
   * The error message.
   */
  error: string;
}
```

## Integration into a Capacitor App

To integrate this plugin into your Capacitor app:

1. **Install the plugin:**

   ```bash
   npm install @exxili/capacitor-nfc
   npx cap sync
   ```

2. **Import the plugin in your code:**

   ```typescript
   import { NFC } from '@exxili/capacitor-nfc';
   ```

3. **Use the plugin methods as described in the [Usage](#usage) section.**

## Example

Here's a complete example of how to read and write NFC tags in your app:

```typescript
import { NFC, NDEFMessages, NDEFWriteOptions, NFCError } from '@exxili/capacitor-nfc';

// Check if NFC is supported
const { supported } = await NFC.isSupported();

// Start NFC scanning -- iOS only
NFC.startScan().catch((error) => {
  console.error('Error starting NFC scan:', error);
});

// Listen for NFC tag detection
NFC.onRead((data: NDEFMessages) => {
  const stringMessages: NDEFMessage<string> = data.string();
  const uint8ArrayMessages: NDEFMessage<Uint8Array> = data.uint8Array();
  
  // Print all Uint8Array payloads
  console.log('Received NFC tag:', stringMessages.messages?.at(0)?.records?.at(0).payload);    // prints string[]
  console.log('Received NFC tag:', uint8ArrayPayloads.messages?.at(0)?.records?.at(0).payload);    // prints Uint8Array[]
});

// Handle NFC errors
NFC.onError((error: NFCError) => {
  console.error('NFC Error:', error);
});

// Prepare an NDEF message to write
const message: NDEFWriteOptions = {
  records: [
    {
      type: 'T', // Text record type
      payload: 'Hello, NFC!',
    },
  ],
};

// Write NDEF message to NFC tag
NFC.writeNDEF(message)
  .then(() => {
    console.log('Write initiated');
  })
  .catch((error) => {
    console.error('Error writing to NFC tag:', error);
  });

// Listen for write success
NFC.onWrite('nfcWriteSuccess', () => {
  console.log('NDEF message written successfully.');
});
```

## License

[MIT License](https://opensource.org/license/mit)

---

**Support**: If you encounter any issues or have questions, feel free to open an issue.

---
