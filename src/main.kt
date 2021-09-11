fun main() {
    val _asm = asm("csgo.exe")
    val client = _asm.get_module_address("client.dll")

    val local = _asm.get_dynamic_address(client + 0xD8C2CC)
    val health = _asm.read_memory<Int>(local + 0x100)

    println(health)
    _asm.close()
}
