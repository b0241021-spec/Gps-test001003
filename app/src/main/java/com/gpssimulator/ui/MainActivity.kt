package com.gpssimulator.ui
import kotlinx.coroutines.flow.collect
import com.gpssimulator.data.SimulationState

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.gpssimulator.R
import com.gpssimulator.data.GPSSimulatorStateManager
import com.gpssimulator.utils.GPSCalculator
import com.gpssimulator.utils.GPSCoordinate
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var googleMap: GoogleMap
    private lateinit var stateManager: GPSSimulatorStateManager
    private var lastSimulationTime = System.currentTimeMillis()

    private lateinit var directionSlider: SeekBar
    private lateinit var speedSlider: SeekBar
    private lateinit var scaleSlider: SeekBar
    private lateinit var simulationSwitch: Switch
    private lateinit var movingSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var directionText: TextView
    private lateinit var speedText: TextView
    private lateinit var scaleText: TextView
    private lateinit var resetButton: Button
    private lateinit var arrowView: ArrowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stateManager = GPSSimulatorStateManager()
        initializeUI()
        initializeMap()
        requestPermissions()
        observeState()
        startSimulationLoop()
    }

    private fun initializeUI() {
        directionSlider = findViewById(R.id.directionSlider)
        speedSlider = findViewById(R.id.speedSlider)
        scaleSlider = findViewById(R.id.scaleSlider)
        simulationSwitch = findViewById(R.id.simulationSwitch)
        movingSwitch = findViewById(R.id.movingSwitch)
        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        directionText = findViewById(R.id.directionText)
        speedText = findViewById(R.id.speedText)
        scaleText = findViewById(R.id.scaleText)
        resetButton = findViewById(R.id.resetButton)
        arrowView = findViewById(R.id.arrowView)

        directionSlider.max = 359
        directionSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    stateManager.updateDirection(progress.toDouble())
                    arrowView.setDirection(progress.toDouble())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        speedSlider.max = 200
        speedSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = progress / 10.0
                    stateManager.updateSpeed(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        scaleSlider.max = 299
        scaleSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val scale = 1.0 + progress
                    stateManager.updateScale(scale)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        simulationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                stateManager.startSimulation()
            } else {
                stateManager.stopSimulation()
                movingSwitch.isChecked = false
            }
        }

        movingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && stateManager.state.value.isSimulating) {
                stateManager.startMoving()
            } else {
                stateManager.stopMoving()
            }
        }

        resetButton.setOnClickListener {
            stateManager.reset()
            directionSlider.progress = 0
            speedSlider.progress = 0
            scaleSlider.progress = 0
            simulationSwitch.isChecked = false
            movingSwitch.isChecked = false
            arrowView.setDirection(0.0)
        }
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            val initialLocation = LatLng(25.0330, 121.5654)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f))
            updateMapMarker()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            stateManager.state.collect { state: SimulationState ->
                locationText.text = "位置: ${state.currentLocation.latitude.format(6)}, ${state.currentLocation.longitude.format(6)}"
                directionText.text = "方向: ${state.direction.toInt()}°"
                speedText.text = "速度: ${state.speed.format(1)} km/hr"
                scaleText.text = "大小: ${state.scale.toInt()}%"
                statusText.text = state.statusMessage

                directionSlider.progress = state.direction.toInt()
                speedSlider.progress = (state.speed * 10).toInt()
                scaleSlider.progress = (state.scale - 1).toInt()

                simulationSwitch.isChecked = state.isSimulating
                movingSwitch.isChecked = state.isMoving && state.isSimulating

                updateMapMarker()
            }
        }
    }

    private fun updateMapMarker() {
        if (!::googleMap.isInitialized) return

        googleMap.clear()
        val state = stateManager.state.value
        val location = LatLng(state.currentLocation.latitude, state.currentLocation.longitude)

        googleMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("GPS 位置")
                .snippet("${state.currentLocation.latitude.format(6)}, ${state.currentLocation.longitude.format(6)}")
        )

        if (state.isSimulating) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(location))
        }
    }

    private fun startSimulationLoop() {
        lifecycleScope.launch {
            while (true) {
                val state = stateManager.state.value
                if (state.isSimulating && state.isMoving && state.speed > 0) {
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = (now - lastSimulationTime) / 1000.0
                    lastSimulationTime = now

                    val newLocation = GPSCalculator.calculateNextLocation(
                        state.currentLocation,
                        state.direction,
                        state.speed,
                        elapsedSeconds.coerceAtMost(1.0)
                    )

                    stateManager.updateLocation(newLocation)
                }
                Thread.sleep(1000)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}

private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
// PATCH_LOOP_1783496544
// PATCH_LOOP_1783646925
// PATCH_LOOP_1783646950
// PATCH_LOOP_1783646986
// PATCH_LOOP_1783647017
