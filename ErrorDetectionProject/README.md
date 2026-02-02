# Error Detection (Number Theory)

A simple error detection demo using a modulo checksum:

- Sender checksum = `sentData mod key/modulus`
- Receiver checksum = `receivedData mod key/modulus`
- If checksums match → no error detected

## Run (Localhost Web UI)

This runs a modern UI in your browser at `http://localhost:8080`.

```powershell
& "C:\Program Files\Java\jdk1.8.0_201\bin\javac.exe" .\ErrorDetectionNumberTheory.java
& "C:\Program Files\Java\jdk1.8.0_201\bin\java.exe" -cp . ErrorDetectionNumberTheory --server
```

To run on a different port:

```powershell
& "C:\Program Files\Java\jdk1.8.0_201\bin\java.exe" -cp . ErrorDetectionNumberTheory --server --port=3000
```

### Web UI features

- Step-by-step workflow timeline (Auto or Step mode)
- Pause/Resume and Stop controls for the simulation
- Multiple error modes (none / delta / digit flip / random delta)
- Run history saved in browser + CSV export
- Dark/Light theme toggle

## Run (GUI - Swing)

From the project folder:

```powershell
& "C:\Program Files\Java\jdk1.8.0_201\bin\javac.exe" .\ErrorDetectionNumberTheory.java
& "C:\Program Files\Java\jdk1.8.0_201\bin\java.exe" -cp . ErrorDetectionNumberTheory
```

## Run (CLI)

```powershell
& "C:\Program Files\Java\jdk1.8.0_201\bin\javac.exe" .\ErrorDetectionNumberTheory.java
& "C:\Program Files\Java\jdk1.8.0_201\bin\java.exe" -cp . ErrorDetectionNumberTheory --cli
```

## Usage (GUI flow)

1. **Sender**: Enter `Data` and `Key/Modulus` (any non-zero integer), then click **Compute checksum**.
2. **Channel**: Click **Copy sent -> received** (optional: **Introduce +1 error**).
3. **Receiver**: Click **Verify** to see whether an error is detected.
