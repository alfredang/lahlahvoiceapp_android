package sg.com.tertiarycourses.lahlahvoice.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.com.tertiarycourses.lahlahvoice.BuildConfig
import sg.com.tertiarycourses.lahlahvoice.R

private const val PRIVACY = "https://www.tertiaryinfotech.com/privacy"
private const val TERMS = "https://www.tertiaryinfotech.com/terms"
private const val DEVELOPER = "https://www.tertiaryinfotech.com"
private const val WHATSAPP = "6588666375"

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("About", "Say what you mean, in any app.") }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.mipmap.ic_launcher), contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("LahLah Voice", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.ink)
                        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Brand.muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "LahLah Voice removes the ums, follows your corrections and writes what you intended. Speech recognition and editing run on your phone, with Gemini Nano where available.",
                    color = Brand.ink,
                )
            }
        }
        item {
            Card {
                Text("Your privacy", fontWeight = FontWeight.SemiBold, color = Brand.ink)
                Text(
                    "No account, no servers, no tracking. Your voice and text stay on this phone. LahLah Voice uses your phone's on-device speech recogniser when it has one.",
                    color = Brand.muted, fontSize = 14.sp,
                )
                Spacer(Modifier.size(8.dp))
                Link("Privacy policy") { open(context, PRIVACY) }
                Link("Terms of service") { open(context, TERMS) }
            }
        }
        item {
            Card {
                Text("Feedback", fontWeight = FontWeight.SemiBold, color = Brand.ink)
                Text("Tell us what works and what doesn't. Your message opens in WhatsApp.", color = Brand.muted, fontSize = 14.sp)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("Title") }, singleLine = true)
                OutlinedTextField(
                    message, { message = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("Your message…") },
                )
                Spacer(Modifier.size(6.dp))
                Button(
                    onClick = {
                        val body = (if (title.isNotBlank()) "*${title.trim()}*\n" else "") + message.trim()
                        open(context, "https://wa.me/$WHATSAPP?text=" + Uri.encode(body))
                    },
                    enabled = message.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                ) { Text("Send feedback") }
            }
        }
        item {
            Card {
                Text("Developer", fontWeight = FontWeight.SemiBold, color = Brand.ink)
                Link("Tertiary Infotech Academy Pte Ltd") { open(context, DEVELOPER) }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(text, color = Brand.ember, fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp))
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
