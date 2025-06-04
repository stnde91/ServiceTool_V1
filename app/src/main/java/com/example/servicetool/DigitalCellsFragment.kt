// WICHTIG: Ersetze "com.example.servicetool" mit DEINEM korrekten Paketnamen!
package com.example.servicetool

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
// import androidx.lifecycle.ViewModelProvider // Import für späteres ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.net.ConnectException
import java.net.SocketTimeoutException

// Datenklasse für die Informationen einer digitalen Zelle
data class DigitalCellInfo(
    val cellId: Int,
    var serialNumber: String = "N/A",
    var baudRate: String = "N/A",
    var version: String = "N/A",
    var status: String = "Lade Daten..."
)

// Datenklasse für Moxa-Befehle (könnte auch aus einem gemeinsamen Ort importiert werden)
// 'name' ist für die interne Logik/Zuordnung, 'moxaRawCommand' ist der echte Befehl an das Gerät.
data class MoxaCommandDigital(val name: String, val moxaRawCommand: String, val type: CommandTypeDigital)
enum class CommandTypeDigital { SERIAL, BAUDRATE, VERSION, OTHER }

class DigitalCellsFragment : Fragment() {

    private lateinit var recyclerViewDigitalCells: RecyclerView
    private lateinit var digitalCellAdapter: DigitalCellAdapter
    private val digitalCellList = mutableListOf<DigitalCellInfo>()

    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Moxa Konfiguration (Idealfall: aus einem ViewModel oder SharedPreferences)
    // Diese Werte sollten aus dem SettingsFragment übernommen werden.
    private var moxaIpAddress = "192.168.0.100" // Standard, sollte konfigurierbar sein
    private var moxaPort = 4001 // Standard, sollte konfigurierbar sein
    private val MOXA_CONNECTION_TIMEOUT_MS = 3000 // Verbindungstimeout in ms
    private val MOXA_READ_TIMEOUT_MS = 3000       // Lesetimeout in ms
    private val NUM_ZELLEN = 8 // Anzahl der Zellen, die abgefragt werden sollen

    // Definiere die Befehle für jede Zelle
    // WICHTIG: Ersetze die 'moxaRawCommand' Strings mit den ECHTEN Befehlen deiner Moxa-Box!
    // Die Zellennummer (cellNum) muss oft Teil des Befehls sein.
    private fun getCommandsForDigitalCell(cellNum: Int): List<MoxaCommandDigital> {
        // Beispielhafte Befehlsstruktur, passe dies an das Protokoll deiner Moxa-Box an.
        // $01I könnte z.B. Infos von Gerät 01 holen.
        // #01SN könnte die Seriennummer von Gerät 01 holen.
        // Stelle sicher, dass Steuerzeichen wie \r\n korrekt verwendet werden, falls nötig.
        return listOf(
            MoxaCommandDigital("Seriennummer Z$cellNum", "#0${cellNum}SN\r\n", CommandTypeDigital.SERIAL),         // PLATZHALTER!
            MoxaCommandDigital("Baudrate Z$cellNum", "#0${cellNum}BAUD\r\n", CommandTypeDigital.BAUDRATE),       // PLATZHALTER!
            MoxaCommandDigital("Version Z$cellNum", "\$0${cellNum}FV\r\n", CommandTypeDigital.VERSION)          // PLATZHALTER!
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        // Du musst eine Layout-Datei erstellen, die den RecyclerView enthält,
        // z.B. res/layout/fragment_digital_cells.xml
        val view = inflater.inflate(R.layout.fragment_digital_cells, container, false)
        recyclerViewDigitalCells = view.findViewById(R.id.recyclerViewDigitalCells) // Initialisierung hier
        setupRecyclerView()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: IP und Port aus einem ViewModel oder SharedPreferences laden.
        // Zum Beispiel:
        // val viewModel = ViewModelProvider(requireActivity()).get(MoxaViewModel::class.java)
        // moxaIpAddress = viewModel.ipAddress.value ?: "192.168.0.100"
        // moxaPort = viewModel.port.value ?: 4001

        Log.d("DigitalCellsFragment", "onViewCreated: Lade Daten für digitale Zellen mit IP: $moxaIpAddress, Port: $moxaPort")
        loadAllDigitalCellData()
    }

    private fun setupRecyclerView() {
        // Stelle sicher, dass recyclerViewDigitalCells initialisiert ist, bevor es verwendet wird.
        // Dies wird durch die Initialisierung in onCreateView gewährleistet.
        digitalCellAdapter = DigitalCellAdapter(digitalCellList)
        recyclerViewDigitalCells.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = digitalCellAdapter
        }
    }

    private fun loadAllDigitalCellData() {
        if (digitalCellList.isNotEmpty() && digitalCellList.all { it.status == "Daten geladen" || it.status.startsWith("Fehler") }) {
            Log.d("DigitalCellsFragment", "Daten bereits geladen oder Ladevorgang abgeschlossen.")
            return
        }

        digitalCellList.clear()
        for (i in 1..NUM_ZELLEN) {
            digitalCellList.add(DigitalCellInfo(cellId = i, status = "Warte auf Abruf..."))
        }
        if (::digitalCellAdapter.isInitialized) {
            digitalCellAdapter.notifyDataSetChanged()
        }


        fragmentScope.launch {
            for (i in 0 until NUM_ZELLEN) {
                val cellNum = i + 1
                digitalCellList[i].status = "Lade Daten für Zelle $cellNum..."
                withContext(Dispatchers.Main) {
                    if (::digitalCellAdapter.isInitialized) digitalCellAdapter.notifyItemChanged(i)
                }

                val commands = getCommandsForDigitalCell(cellNum)
                var hasCellError = false
                for (command in commands) {
                    try {
                        val response = executeMoxaCommandOnDevice(command.moxaRawCommand)
                        if (response.startsWith("Fehler:")) {
                            hasCellError = true
                        }
                        when (command.type) {
                            CommandTypeDigital.SERIAL -> digitalCellList[i].serialNumber = response
                            CommandTypeDigital.BAUDRATE -> digitalCellList[i].baudRate = response
                            CommandTypeDigital.VERSION -> digitalCellList[i].version = response
                            CommandTypeDigital.OTHER -> Log.d("DigitalCellsFragment", "Antwort für OTHER Befehl ${command.name}: $response")
                        }
                    } catch (e: Exception) {
                        Log.e("DigitalCellsFragment", "Fehler bei Befehl ${command.name} für Zelle $cellNum", e)
                        when (command.type) {
                            CommandTypeDigital.SERIAL -> digitalCellList[i].serialNumber = "Fehler"
                            CommandTypeDigital.BAUDRATE -> digitalCellList[i].baudRate = "Fehler"
                            CommandTypeDigital.VERSION -> digitalCellList[i].version = "Fehler"
                            else -> { /* nichts tun */ }
                        }
                        hasCellError = true
                    }
                }
                digitalCellList[i].status = if (hasCellError) "Fehler beim Laden" else "Daten geladen"
                withContext(Dispatchers.Main) {
                    if (::digitalCellAdapter.isInitialized) digitalCellAdapter.notifyItemChanged(i)
                }
                if (i < NUM_ZELLEN - 1) {
                    delay(100)
                }
            }
            Log.d("DigitalCellsFragment", "Alle digitalen Zellendaten geladen.")
        }
    }

    private suspend fun executeMoxaCommandOnDevice(rawCommand: String): String {
        Log.d("MoxaCommDigital", "Sende Befehl: '${rawCommand.replace("\r\n", "\\r\\n")}' an $moxaIpAddress:$moxaPort")
        return withContext(Dispatchers.IO) {
            try {
                Socket(moxaIpAddress, moxaPort).use { socket ->
                    socket.soTimeout = MOXA_READ_TIMEOUT_MS

                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                    writer.print(rawCommand)
                    Log.d("MoxaCommDigital", "Befehl '${rawCommand.replace("\r\n", "\\r\\n")}' gesendet. Warte auf Antwort...")
                    val response = reader.readLine()

                    if (response != null) {
                        Log.i("MoxaCommDigital", "Antwort von Moxa: '$response'")
                        return@withContext response.trim()
                    } else {
                        Log.w("MoxaCommDigital", "Keine Antwort von Moxa empfangen (null) für Befehl: ${rawCommand.replace("\r\n", "\\r\\n")}.")
                        return@withContext "Fehler: Keine Antwort"
                    }
                }
            } catch (e: ConnectException) {
                Log.e("MoxaCommDigital", "Verbindungsfehler zu $moxaIpAddress:$moxaPort: ${e.message}", e)
                return@withContext "Fehler: Verbindung"
            } catch (e: SocketTimeoutException) {
                Log.e("MoxaCommDigital", "Timeout beim Warten auf Antwort von $moxaIpAddress:$moxaPort: ${e.message}", e)
                return@withContext "Fehler: Timeout"
            } catch (e: Exception) {
                Log.e("MoxaCommDigital", "Allgemeiner Fehler bei Moxa-Kommunikation: ${e.message}", e)
                return@withContext "Fehler: ${e.message?.take(30)}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentScope.cancel()
        Log.d("DigitalCellsFragment", "onDestroyView: Coroutines gecancelt.")
    }
}

class DigitalCellAdapter(private val cellList: List<DigitalCellInfo>) :
    RecyclerView.Adapter<DigitalCellAdapter.DigitalCellViewHolder>() {

    class DigitalCellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewCellTitleDigital)
        val serialNumberTextView: TextView = itemView.findViewById(R.id.textViewSerialNumber)
        val baudRateTextView: TextView = itemView.findViewById(R.id.textViewBaudRate)
        val versionTextView: TextView = itemView.findViewById(R.id.textViewVersion)
        val statusTextView: TextView = itemView.findViewById(R.id.textViewDigitalCellStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DigitalCellViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_digital_cell, parent, false)
        return DigitalCellViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DigitalCellViewHolder, position: Int) {
        val currentItem = cellList[position]

        holder.titleTextView.text = "Zelle ${currentItem.cellId}"
        holder.serialNumberTextView.text = currentItem.serialNumber
        holder.baudRateTextView.text = currentItem.baudRate
        holder.versionTextView.text = currentItem.version
        holder.statusTextView.text = "Status: ${currentItem.status}"
    }

    override fun getItemCount() = cellList.size
}
