package com.example.servicetool; // Dein Paketname

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
// Importiere hier ggf. dein ViewModel

class SettingsFragment : Fragment() {

    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewConnectionStatus: TextView
    // private lateinit var moxaViewModel: MoxaViewModel // Beispiel für ViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editTextIpAddress = view.findViewById(R.id.editTextIpAddress)
        editTextPort = view.findViewById(R.id.editTextPort)
        buttonConnect = view.findViewById(R.id.buttonConnect)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)

        // Lade gespeicherte IP/Port (z.B. aus SharedPreferences oder ViewModel)
        // editTextIpAddress.setText(moxaViewModel.ipAddress.value)
        // editTextPort.setText(moxaViewModel.port.value?.toString())


        buttonConnect.setOnClickListener {
            val ip = editTextIpAddress.text.toString()
            val portStr = editTextPort.text.toString()

            if (ip.isNotBlank() && portStr.isNotBlank()) {
                val port = portStr.toIntOrNull()
                if (port != null) {
                    // Hier Logik zum Speichern der IP/Port (z.B. im ViewModel)
                    // und zum Testen der Verbindung aufrufen
                    // moxaViewModel.setConnectionDetails(ip, port)
                    // moxaViewModel.testConnection()
                    textViewConnectionStatus.text = "Verbinde mit $ip:$port..."
                    // Nach dem Test den Status aktualisieren
                } else {
                    textViewConnectionStatus.text = "Ungültiger Port"
                }
            } else {
                textViewConnectionStatus.text = "IP und Port eingeben"
            }
        }
        // Beobachte Verbindungsstatus vom ViewModel
        // moxaViewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
        //    textViewConnectionStatus.text = "Status: $status"
        // }
    }
}