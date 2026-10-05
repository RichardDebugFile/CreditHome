package com.alfas.amortizacioncredito.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.R
import kotlinx.coroutines.delay
import com.alfas.amortizacioncredito.ui.theme.*

@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animación de entrada inicial (Fade & Scale In)
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
    val transition = rememberTransition(transitionState, label = "splash_entry")

    val entranceAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 800, easing = LinearOutSlowInEasing) },
        label = "entrance_alpha"
    ) { state -> if (state) 1f else 0f }

    val entranceScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 800, easing = FastOutSlowInEasing) },
        label = "entrance_scale"
    ) { state -> if (state) 1f else 0.75f }

    // Animación continua de halo palpitante
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_and_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_glow"
    )

    // Barra de progreso animada de 0% a 100%
    val progress = remember { Animatable(0f) }

    // Tiempo de carga y animación de la barra (llega al 100% y luego transiciona a la app)
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            )
        )
        // Breve pausa para apreciar la carga al 100% completa
        delay(180)
        onTimeout()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Slate800, // Centro azul pizarra profundo
                        Slate900, // Slate 900
                        Slate950  // Fondo ultra oscuro
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(entranceScale)
                .alpha(entranceAlpha)
                .padding(24.dp)
        ) {
            // Contenedor del Logotipo con Aura Radiante
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                // Halo de fondo con gradiente giratorio Esmeralda - Azul
                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .rotate(rotationAngle)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    CreditHomeEmerald.copy(alpha = haloAlpha),
                                    CreditHomeBlue.copy(alpha = haloAlpha * 0.7f),
                                    CreditHomeSky.copy(alpha = haloAlpha * 0.9f),
                                    CreditHomeEmerald.copy(alpha = haloAlpha)
                                )
                            )
                        )
                )

                // Capa de desenfoque/sombra interior
                Box(
                    modifier = Modifier
                        .size(122.dp)
                        .clip(CircleShape)
                        .background(Slate900)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(CreditHomeEmeraldLight, CreditHomeBlue)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Logotipo oficial de la app
                    Image(
                        painter = painterResource(id = R.drawable.ic_credithome_logo),
                        contentDescription = "CreditHome Logo",
                        modifier = Modifier.size(92.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Nombre de la App con Gradiente
            Text(
                text = "CreditHome",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Badge de subtítulo moderno
            Surface(
                color = CreditHomeEmeraldDark.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CreditHomeEmerald.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CreditHomeEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gestor Financiero & Amortización",
                        color = CreditHomeEmeraldLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(42.dp))

            // Barra de Carga Elegante con Shimmer
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Slate700)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(CreditHomeEmerald, CreditHomeSky)
                            )
                        )
                )
            }
        }

        // Footer versión con detalle de seguridad
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "CreditHome • Bóveda Segura v1.0",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
