<h1 align="center">
    <img src="./logo.svg" height="200" alt="JPEG2K Logo"/><br>
    JPEG2K
</h1>

A fork of [JP2ForAndroid](https://github.com/ThalesGroup/JP2ForAndroid) which is a JPEG2000 wrapper
for Android using [OpenJPEG](https://github.com/uclouvain/openjpeg).

## Key Improvements in this Fork

This fork represents a complete modernization of the original library, migrating the codebase to
Kotlin, upgrading the build system to Gradle 9+, and updating the underlying OpenJPEG engine.

### Modernization & Build System

- Full Kotlin Migration: The project source code has been transitioned from Java to Kotlin.
- Gradle 9.x & Kotlin DSL: Updated Gradle to version 9.2.1 and migrated all build scripts to Kotlin
  DSL (.kts).
- Version Catalog: Implemented a version catalog (libs.versions.toml) for centralized dependency
  management.
- Modern Android Standards: Added namespace support, updated the NDK and CMake versions, and
  upgraded the Android Gradle Plugin (AGP).

### Core Engine & Native Code

- OpenJPEG 2.5.4: Updated the core OpenJPEG library to version 2.5.4.
- C++ Modernization: Replaced deprecated libraries, updated syntax (replaced null with nullptr), and
  renamed JNI variables (e.g., in to inputStream) to prevent conflicts with Kotlin keywords.
- Compatibility: Removed incompatible premultiplication functions while extending test app
  compatibility beyond Android 11.

### Architecture & Publishing

- Maven Central Ready: Removed defunct Bintray support and added configuration for publishing to
  Maven Central.
- Library Structure: Correctly converted the module from an Application type to a Library type.
- MVVM Pattern: Replaced legacy async operations with a modern ViewModel architecture in the example
  app.
- Open Source Compliance: Project is now licensed under Apache 2.0, with open-source licenses
  embedded directly into the AAR file.

## Set up

### Kotlin DSL

Add dependency to your `build.gradle.kts`:

```kotlin
implementation("com.andymic.jpeg2k:jpeg2k:1.0.0")
```

### Groovy

Add dependency to your `build.gradle`:

```groovy
implementation 'com.andymic.jpeg2k:jpeg2k:1.0.0'
```

## Basic Usage

Decoding an image:

```kotlin
val bmp: Bitmap = JP2Decoder(jp2data).decode()
imgView.setImageBitmap(bmp)
```

Encoding an image:

```kotlin
// Lossless Encoding
val jp2data: ByteArray = new JP2Encoder (bmp).encode()
// Lossy Encoding (target PSNR = 50dB)
val jp2data: ByteArray = JP2Encoder(bmp)
    .setVisualQuality(50)
    .encode()
```

## Advanced Usage

### Multiple Resolutions

A single JPEG-2000 image can contain multiple resolutions.
The final resolution is always equal to `<image_width> x <image_height>`; each additional resolution
reduces the width and height by the factor of two. If you don't need the full resolution, you can
save memory and decoding time by decoding at a lower resolution.

#### Encoding

Default number of resolutions is 6, but you can specify your own value:

```kotlin
val jp2data: ByteArray = JP2Encoder(bmp)
    .setNumResolutions(3)
    .encode()
```

The number of resolutions must be between 1 and 32 and both image width and height must be greater
or equal to $`2^{numResolutions - 1}`$.

#### Decoding

You can obtain the number of resolutions (as well as some other information about the image) by
calling the `readHeader()` method:

```kotlin
val header: Header = JP2Decoder(jp2data).readHeader()
val numResolutions: Int = header.numResolutions
```

If you don't need the full resolution image, you can skip one or more resolutions during the
decoding process.

```kotlin
val reducedBmp: Bitmap = JP2Decoder(jp2data)
    .setSkipResolutions(2)
    .decode()
```

### Multiple Quality Layers

Multiple layers can be encoded in a JPEG-2000 image, each having a different visual quality. If you
don't need maximum visual quality, you can save decoding time by skipping the higher-quality layers.

#### Encoding

Quality layers can be specified in two ways: as a list of compression ratios or visual qualities.
The **compression ratios** are specified as factors of compression, i.e. 20 means the size will be
20 times less than the raw uncompressed size. Compression ratio 1 means lossless compression.

Example:

```kotlin
// Specifying 3 quality layers with compression ratios of 1:50, 1:20, and lossless.
val jp2data: ByteArray = JP2Encoder(bmp)
    .setCompressionRatio(50, 10, 1)
    .encode()
```

**Visual quality** is specified as [PSNR](https://en.wikipedia.org/wiki/Peak_signal-to-noise_ratio)
values in dB. Usable values are roughly between 20 (very aggressive compression) and 70 (almost
lossless). Special value 0 indicates lossless compression.

Example:

```kotlin
// Specifying 3 quality layers with PSNRs of 30dB, 50dB, and lossless.
val jp2data: ByteArray = new JP2Encoder (bmp)
    .setVisualQuality(30, 50, 0)
    .encode()
```

`setCompressionRatio()` and `setVisualQuality()` can not be used at the same time.

#### Decoding

You can obtain the number of available quality layers by calling the `readHeader()` method:

```kotlin
val header: Header = JP2Decoder(jp2data).readHeader()
val numQualityLayers: Int = header.numQualityLayers
```

If you don't need a maximum quality image, you can trade some visual quality for a shorter decoding
time by not decoding all the quality layers.

```kotlin
val lowQualityBmp: Bitmap = new JP2Decoder (jp2data)
    .setLayersToDecode(2)
    .decode()
```

### File Format

`JP2Encoder` supports two output formats:

* JP2 - standard JPEG-2000 file format (encapsulating a JPEG-2000 codestream)
* J2K - unencapsulated JPEG-2000 codestream

JP2 is the default output format, but it can be changed:

```kotlin
val j2kData: Bitmap = new JP2Encoder (bmp)
    .setOutputFormat(FORMAT_J2K)
    .encode()
```
