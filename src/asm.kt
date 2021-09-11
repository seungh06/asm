import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Psapi.MODULEINFO
import com.sun.jna.platform.win32.WinDef.HMODULE
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.lang.Exception

class asm (process_name: String){
    companion object {
        private val psapi = Native.load("psapi", psapi::class.java)
        val kernel32 = Native.load("kernel32", kernel32::class.java)

        fun open_process(permission: Int, process_id: Int)
            = kernel32.OpenProcess(permission, false, process_id)

        fun get_process_id(target: String): Int {
            val processes = IntArray(1024)
            psapi.EnumProcesses(processes, processes.size)

            processes.forEach { process ->
                if(process == 0) return@forEach
                val handle = open_process(permission.process_all_access, process)

                val basename = ByteArray(512)
                psapi.GetModuleBaseNameA(handle, HMODULE(), basename, 512)

                kernel32.CloseHandle(handle)
                if(Native.toString(basename) == target) return process
            }
            throw Exception("cannot find process.")
        }

        fun print(address: Long)
            = println(String.format("0x%08x", address))

        fun Pointer?.to_native()
            = Pointer.nativeValue(this)
    }

    object permission {
        val query_information = 0x0400
        val process_vm_read = 0x0010
        val process_vm_write = 0x0020
        val process_vm_operation = 0x0008
        val process_all_access = 0x001F0FFF
    }

    data class module(val module_name: String, val base_address: Long, val entry_point: Long, val size: Int)
    data class process_info(val id: Int, val handle: HANDLE)

    val process = get_process_id(process_name).let { id ->
        process_info(id, open_process(permission.process_all_access, id))
    }

    fun close()
        = kernel32.CloseHandle(process.handle)

    fun get_modules(size: Int = 2048): List<module> {
        val modules = arrayOfNulls<HMODULE>(size)
        val needed = IntByReference()
        psapi.EnumProcessModulesEx(process.handle, modules, modules.size, needed, 0x03)

        val res_modules: MutableList<module> = mutableListOf()
        for(x in 0 until needed.value / 4) {
            val module = ByteArray(256)
            psapi.GetModuleBaseNameA(process.handle, modules[x], module, module.size)

            val info = MODULEINFO()
            psapi.GetModuleInformation(process.handle, modules[x], info, info.size())

            if(info.lpBaseOfDll.to_native() == 0L) break

            res_modules.add(
                module(Native.toString(module), info.lpBaseOfDll.to_native(), info.EntryPoint.to_native(), info.SizeOfImage)
            )
        }
        return res_modules.toList()
    }

    fun get_module(module_name: String): module {
        get_modules().forEach { module ->
            if(module_name == module.module_name) return module
        }
        throw Exception("cannot find module.")
    }

    fun get_module_address(module_name: String)
        = get_module(module_name).base_address

    fun get_dynamic_address(base_address: Long, vararg offsets: Int): Long {
        val memory = Memory(8)
        kernel32.ReadProcessMemory(process.handle, base_address, memory, 8)

        var address = memory.getInt(0).toLong()
        for(x in offsets.indices) {
            address = memory.getInt(0) + offsets[x].toLong()
            if(x != offsets.size -1) kernel32.ReadProcessMemory(process.handle, address, memory, 8)
        }
        return address
    }

    inline fun <reified T> read_memory(address: Long, size: Int = 8): T {
        val memory = Memory(size.toLong())
        kernel32.ReadProcessMemory(process.handle, address, memory, size)

        return when(T::class.simpleName.toString().toLowerCase()) {
            "bytearray" -> memory.getByteArray(0, size)
            "short" -> memory.getShort(0)
            "int" -> memory.getInt(0)

            "float" -> memory.getFloat(0)
            "double" -> memory.getDouble(0)

            "string" -> memory.getString(0)
            "char" -> memory.getChar(0)
            else -> throw Exception("unsupported type request." +
                    "\nsupport type -> bytearray, short, int, float, double, string, char.")
        } as T
    }

    private data class write_inf(val data: ByteArray, val size: Int)

    fun <T> write_memory(address: Long, data: T) {
        val write_input: write_inf
                = when(data) {
            is ByteArray -> write_inf(data, data.size)
            is Short -> write_inf(bit.short_to_byte(data), 2)
            is Int -> write_inf(bit.int_to_byte(data), 4)

            is Float -> write_inf(bit.float_to_byte(data), 4)
            is Double -> write_inf(bit.double_to_byte(data), 8)

            is Boolean -> write_inf(bit.boolean_to_byte(data), 2)

            is String -> bit.string_to_byte(data).let { buffer -> write_inf(buffer, buffer.size) }
            is Char -> bit.char_to_byte(data).let { buffer -> write_inf(buffer, buffer.size) }

            else -> throw Exception("unsupported type request."
                    + "\nsupport type -> bytearray, short, int, float, double, boolean, string, char.")
        }
        kernel32.WriteProcessMemory(process.handle, address, write_input.data, write_input.size)
    }
}

class bit {
    companion object {
        fun int_to_byte(input: Int)
                = int_array_to_byte(intArrayOf(input ushr 24, input ushr 16, input ushr 8, input))
        fun long_to_byte(input: Long)
                = byteArrayOf((input ushr 56).toByte(), (input ushr 48).toByte(), (input ushr 40).toByte(), (input ushr 32).toByte(),
            (input ushr 24).toByte(), (input ushr 16).toByte(), (input ushr 8).toByte(), input.toByte())
        fun short_to_byte(input: Short)
                = int_array_to_byte(intArrayOf(input.toInt() ushr 8, input.toInt()))
        fun float_to_byte(input: Float)
                = int_to_byte(java.lang.Float.floatToIntBits(input))
        fun double_to_byte(input: Double)
                = long_to_byte(java.lang.Double.doubleToRawLongBits(input))
        fun char_to_byte(input: Char)
                = int_array_to_byte(intArrayOf(input.toInt() and 0xff, input.toInt() shr 8 and 0xff))
        fun string_to_byte(input: String)
                = input.toByteArray()
        fun boolean_to_byte(input: Boolean)
                = byteArrayOf((if (input) 1 else 0).toByte())
        fun int_array_to_byte(input: IntArray)
                = input.foldIndexed(ByteArray(input.size)) { index, acc, i -> acc.apply { set(index, i.toByte()) } }
    }
}

interface kernel32: StdCallLibrary {
    fun OpenProcess(access: Int, inherit: Boolean, process_id: Int): HANDLE
    fun CloseHandle(handle: HANDLE): Boolean
    fun ReadProcessMemory(process: HANDLE, address: Long, buffer: Pointer, size: Int, outNumberOfBytesRead: IntByReference? = null): Boolean
    fun WriteProcessMemory(process: HANDLE, address: Long, buffer: ByteArray, size: Int, written: IntByReference? = null): Boolean
}

interface psapi: StdCallLibrary {
    fun EnumProcesses(process: IntArray, size: Int, needed: IntByReference? = null): Boolean
    fun EnumProcessModulesEx(process: HANDLE, modules: Array<HMODULE?>, size: Int, needed: IntByReference, flag: Int)
    fun GetModuleBaseNameA(process: HANDLE, module: HMODULE?, lpBaseName: ByteArray, size: Int): Int
    fun GetModuleInformation(process: HANDLE, module: HMODULE?, info: MODULEINFO, size: Int): Boolean
}