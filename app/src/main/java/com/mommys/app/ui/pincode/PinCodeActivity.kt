package com.mommys.app.ui.pincode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityPincodeBinding
import com.mommys.app.ui.main.MainActivity

class PinCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPincodeBinding
    private lateinit var preferencesManager: PreferencesManager
    
    private var currentPin = StringBuilder()
    private var isSettingPin = false
    private var confirmPin = ""

    companion object {
        const val MODE_VERIFY = 0
        const val MODE_SET = 1
        const val MODE_CHANGE = 2
        const val EXTRA_MODE = "mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPincodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        val mode = intent.getIntExtra(EXTRA_MODE, MODE_VERIFY)
        isSettingPin = mode == MODE_SET || mode == MODE_CHANGE
        
        setupUI()
        setupKeypad()
    }

    private fun setupUI() {
        if (isSettingPin) {
            binding.txtTitle.text = getString(R.string.pin_set_title)
            binding.txtSubtitle.text = getString(R.string.pin_set_subtitle)
        } else {
            binding.txtTitle.text = getString(R.string.pin_enter_title)
            binding.txtSubtitle.text = getString(R.string.pin_enter_subtitle)
        }
        
        updatePinDisplay()
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )
        
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { onDigitPressed(index.toString()) }
        }
        
        binding.btnBackspace.setOnClickListener { onBackspacePressed() }
        binding.btnCancel.setOnClickListener { onCancelPressed() }
    }

    private fun onDigitPressed(digit: String) {
        if (currentPin.length < 4) {
            currentPin.append(digit)
            updatePinDisplay()
            
            if (currentPin.length == 4) {
                onPinComplete()
            }
        }
    }

    private fun onBackspacePressed() {
        if (currentPin.isNotEmpty()) {
            currentPin.deleteCharAt(currentPin.length - 1)
            updatePinDisplay()
        }
    }

    private fun onCancelPressed() {
        if (isSettingPin) {
            finish()
        } else {
            // No permitir cancelar en modo verificación
            finishAffinity()
        }
    }

    private fun updatePinDisplay() {
        val indicators = listOf(
            binding.pinIndicator1, binding.pinIndicator2,
            binding.pinIndicator3, binding.pinIndicator4
        )
        
        indicators.forEachIndexed { index, indicator ->
            indicator.isSelected = index < currentPin.length
        }
    }

    private fun onPinComplete() {
        val enteredPin = currentPin.toString()
        
        if (isSettingPin) {
            if (confirmPin.isEmpty()) {
                // Primera entrada, guardar y pedir confirmación
                confirmPin = enteredPin
                currentPin.clear()
                updatePinDisplay()
                binding.txtSubtitle.text = getString(R.string.pin_confirm_subtitle)
            } else {
                // Confirmar pin
                if (enteredPin == confirmPin) {
                    val pinInt = enteredPin.toIntOrNull()
                    if (pinInt != null && pinInt in 0..9999) {
                        preferencesManager.pinValue = pinInt
                    }
                    Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    // No coinciden
                    Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                    confirmPin = ""
                    currentPin.clear()
                    updatePinDisplay()
                    binding.txtSubtitle.text = getString(R.string.pin_set_subtitle)
                }
            }
        } else {
            // Verificar pin
            if (enteredPin == preferencesManager.pinCode) {
                navigateToMain()
            } else {
                Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
                currentPin.clear()
                updatePinDisplay()
                // Vibrar
                binding.root.animate()
                    .translationX(-20f)
                    .setDuration(50)
                    .withEndAction {
                        binding.root.animate()
                            .translationX(20f)
                            .setDuration(50)
                            .withEndAction {
                                binding.root.animate()
                                    .translationX(0f)
                                    .setDuration(50)
                            }
                    }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
