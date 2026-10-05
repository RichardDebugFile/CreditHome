package com.alfas.amortizacioncredito.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.util.CountryPhoneCode
import com.alfas.amortizacioncredito.util.ValidationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPhoneInputField(
    selectedCountry: CountryPhoneCode,
    phoneNumber: String,
    onCountryChange: (CountryPhoneCode) -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Teléfono / Celular (Opcional)",
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    var showCountryPickerModal by remember { mutableStateOf(false) }

    // Campo de Texto Unificado con Selector de País Integrado en el LeadingIcon
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = { input ->
            val filtered = ValidationUtils.filterDigitsOnly(input, selectedCountry.maxDigits + 1)
            onPhoneChange(filtered)
        },
        label = { Text(label, maxLines = 1) },
        placeholder = { Text(selectedCountry.samplePlaceholder) },
        leadingIcon = {
            Surface(
                onClick = { showCountryPickerModal = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(start = 6.dp, end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${selectedCountry.flagEmoji} ${selectedCountry.dialCode}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Cambiar país",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )

    // Modal para Seleccionar Código de País
    if (showCountryPickerModal) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredCountries = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                CountryPhoneCode.ALL_COUNTRIES
            } else {
                CountryPhoneCode.ALL_COUNTRIES.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.dialCode.contains(searchQuery) ||
                            it.isoCode.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCountryPickerModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar País", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar país o código...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCountries, key = { it.isoCode }) { country ->
                            val isSelected = country == selectedCountry
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onCountryChange(country)
                                        showCountryPickerModal = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(country.flagEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = country.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = country.dialCode,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryPickerModal = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
