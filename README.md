## 🔓 ASM
> External process memory tool for **windows**.

### 🕹 Init
The constructor of the asm class receives the name of the specified process, opens a handle and stores it in ```process```, or we can use ```open_process``` and ```get_process_id``` functions, but not recommended.
```kotlin
val _asm = asm(target: String)

val pid = _asm.process.id
val handle = _asm.process.handle
```
```kotlin
val permission = asm.permission.process_all_access

val pid = asm.get_process_id(target: String): Int
val handle = asm.open_process(permission: Int, process_id: Int): HANDLE
```

> Access permissions for a process are defined in the **asm.permission** object.

### 📎 Module
Gets and searches the modules of the process to get the module's name, base address, entry point, and size.
```kotlin
_asm.get_modules(size: Int = 2048): List<module>
_asm.get_module(target: String): module
_asm.get_module_address(taget: String): Long

module(module_name: String, base_address: Long, entry_point: Long, size: Int)
```

### 🔬 Dynamic Address
Calculate the dynamic address using the ```find_dynamic_address``` function.
```kotlin
_asm.get_dynamic_address(base_address: Long, ...offsets: Int): Long
```
### 💿 Read Memory
Process memory can be read using the ```read_memory``` function. Type must be defined. (Type-safe)
```kotlin
_asm.read_memory<T>(address: Long): T
```

> The supported types are bytearray, short, int, float, double, string, char.

### 🔨 Write Memory
Write process memory using ```write_memory``` function.
```kotlin
_asm.write_memory(address: Long, data: T)
```
> The supported types are bytearray, short, int, float, double, **boolean**, string, char.

### 🔌 Close
To prevent memory leaks, close the handle using the ```close``` function when work is done.
```kotlin
_asm.close()
```

### 🧰 Example
**CS:GO GET LOCALPLAYER HEALTH**
```kotlin
val _asm = asm("csgo.exe")
val client = _asm.get_module_address("client.dll")
val local = _asm.get_dynamic_address(client + 0xD8C2CC)

val health = _asm.read_memory<Int>(local + 0x100)
println(health)

_asm.close()
```

**GEOMETRY DASH SET ATTEMPT TO ZERO**
```kotlin
val _asm = asm("GeometryDash.exe")
val main = _asm.get_module_address("GeometryDash.exe")
val pointer = _asm.get_dynamic_address(main + 0x3212D0, 0x164, 0x498)

_asm.write_memory(pointer, 0)
_asm.close()
```

### 📌 Exceptions
> cannot find process.

```get_process_id``` - Occurs when process does not exist or cannot be found.

> cannot find module.

```get_module``` - Occurs when the module does not exist or cannot be found.


## 📋 License
Distributed under the MIT License. See ```LICENSE``` for more information.

**© 2021 IXFX, All rights reserved.**
