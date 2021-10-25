package com.example.b2i

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log.d
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.example.b2i.databinding.FragmentFirstBinding
import com.google.android.gms.location.*
import java.util.*
import kotlin.collections.HashMap

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private var bluetoothManager: BluetoothManager? = null

    private val serviceUuid: ParcelUuid =
        ParcelUuid.fromString("00001201-0000-1000-8000-00805f9b34fb")

    private var scanMode = false
    private val broadcastMode get() = !scanMode

    private var deviceId = generateId(1)

    private val devices = HashMap<String, MutableList<Pair<Location, Date>>>()

    private var lastLocation: Location? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            d("onStartFailure", "onStartFailure(${errorCode})")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            d("onStartSuccess", "$settingsInEffect")
        }
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            result.device ?: return
            result.scanRecord?.serviceData ?: return
            if (result.scanRecord!!.serviceData.containsKey(serviceUuid)) {
                d("onScanResult", "${callbackType}, $result")
                val data = String(result.scanRecord!!.serviceData!![serviceUuid]!!)
                    .split(',')

                val id = data[0]

                d("deviceId", id)

                val location = Location("B2I")
                location.latitude = data[1].toDouble()
                location.longitude = data[2].toDouble()
                location.speed = data[3].toFloat()

                val time = Calendar.getInstance().time

                if (!devices.containsKey(id))
                    devices[id] = mutableListOf(Pair(location, time))
                else if (devices[id]!!.last().first.latitude != location.latitude
                    || devices[id]!!.last().first.longitude != location.longitude
                )
                    devices[id]!!.add(Pair(location, time))

                var text = ""
                for (entry in devices) {
                    text += "${entry.key}" +
                            "\n${entry.value.size} locations known" +
                            "\nGPS Speed: ${entry.value.last().first.speed} m/s" +
                            "\nCalculated Speed: ${calculateSpeed(entry.value.takeLast(5))}" +
                            "\nDistance: ${
                                calculateDistance(
                                    lastLocation ?: entry.value.last().first,
                                    entry.value.last().first
                                )
                            }" +
                            "\n\n"
                }
                _binding?.scanText?.text = text
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _binding?.scanText?.let { it.text = "Scan failed: Error $errorCode" }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.permissionsButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }

        binding.modeCheckbox.setOnClickListener {
            if (it is CheckBox) {
                scanMode = it.isChecked
                onChangeMode()
            }
        }

        bluetoothManager = requireContext().getSystemService()

        requestLocationUpdates()
        onChangeMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onChangeMode() {
        if (scanMode) {
            startScan()
        } else {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    private fun startScan() {
        val scanFilter = mutableListOf(
            ScanFilter.Builder()
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothManager?.adapter?.bluetoothLeScanner?.startScan(
            scanFilter,
            scanSettings,
            scanCallback
        )
    }

    private fun onLocationUpdate(location: Location) {
        lastLocation = location
        updateLocationDisplay(location)
        if (broadcastMode)
            startLocationBroadcast(location)
    }

    private fun updateLocationDisplay(location: Location) {
        _binding?.coordinatesText?.text =
            "\nLongitude: ${location.longitude}" +
                    "\nLatitude: ${location.latitude}" +
                    "\nBearing: ${location.bearing}" +
                    "\nSpeed: ${location.speed}"
    }

    private fun startLocationBroadcast(location: Location) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val payload =
            "$deviceId,${location.latitude},${location.longitude},${"%.2f".format(location.speed)}"
        d("payload", payload)
        val byteArray = payload.toByteArray()
        d("payloadLength", "${byteArray.size}")

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(serviceUuid, byteArray)
            .build()

        bluetoothManager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)

        bluetoothManager?.adapter?.bluetoothLeAdvertiser?.startAdvertising(
            settings,
            data,
            advertiseCallback,
        )
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            binding.permissionsButton.visibility = View.GONE
            val locationRequest: LocationRequest = LocationRequest.create()
                .setInterval(500)
                .setFastestInterval(50)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

            val locationCallback: LocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation ?: return
                    onLocationUpdate(result.lastLocation)
                }
            }

            LocationServices.getFusedLocationProviderClient(requireContext())
                .requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
        }
    }

    private fun calculateSpeed(locations: List<Pair<Location, Date>>): Double {
        var totalMeters = 0.0
        var totalSeconds = 0.0

        for (i in 0..locations.size - 2) {
            totalMeters += calculateDistance(locations[i].first, locations[i + 1].first)
            totalSeconds += (locations[i + 1].second.time - locations[i].second.time) / 1000
        }

        return totalMeters / totalSeconds
    }

    private fun calculateDistance(a: Location, b: Location): Float {
        val distance = floatArrayOf(0f)
        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            distance
        )
        return distance[0]
    }

    private fun generateId(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}