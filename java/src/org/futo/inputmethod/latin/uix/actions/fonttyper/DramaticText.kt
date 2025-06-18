package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Bitmap
import org.futo.inputmethod.latin.uix.theme.loadBitmapFromAssets
import org.futo.inputmethod.latin.uix.theme.renderShaderToBitmap
import org.futo.inputmethod.latin.R

object DramaticTextRenderer : WordImageRenderer() {
    override val isSlow = true
    override val useZealousCrop = false // Image is fixed size

    override val name: Int
        get() = R.string.action_fonttyper_preset_title_dramatictext

    @Suppress("HardCodedStringLiteral")
    private val shaderToyShader = """
#define _A                                                      \
    if (space > 0.0 && space < 0.4637673020362854 && char == 0) \
        char = 65, charw = 0.4637673020362854, offs = space;    \
    space -= 0.4637673020362854;
#define _B                                                      \
    if (space > 0.0 && space < 0.4439989149570465 && char == 0) \
        char = 66, charw = 0.4439989149570465, offs = space;    \
    space -= 0.4439989149570465;
#define _C                                                       \
    if (space > 0.0 && space < 0.44512851238250734 && char == 0) \
        char = 67, charw = 0.44512851238250734, offs = space;    \
    space -= 0.44512851238250734;
#define _D                                                       \
    if (space > 0.0 && space < 0.44456363916397096 && char == 0) \
        char = 68, charw = 0.44456363916397096, offs = space;    \
    space -= 0.44456363916397096;
#define _E                                                      \
    if (space > 0.0 && space < 0.4053091526031494 && char == 0) \
        char = 69, charw = 0.4053091526031494, offs = space;    \
    space -= 0.4053091526031494;
#define _F                                                     \
    if (space > 0.0 && space < 0.398813796043396 && char == 0) \
        char = 70, charw = 0.398813796043396, offs = space;    \
    space -= 0.398813796043396;
#define _G                                                     \
    if (space > 0.0 && space < 0.446258020401001 && char == 0) \
        char = 71, charw = 0.446258020401001, offs = space;    \
    space -= 0.446258020401001;
#define _H                                                       \
    if (space > 0.0 && space < 0.44456369876861573 && char == 0) \
        char = 72, charw = 0.44456369876861573, offs = space;    \
    space -= 0.44456369876861573;
#define _I                                                      \
    if (space > 0.0 && space < 0.2960180759429932 && char == 0) \
        char = 73, charw = 0.2960180759429932, offs = space;    \
    space -= 0.2960180759429932;
#define _J                                                      \
    if (space > 0.0 && space < 0.4428691387176514 && char == 0) \
        char = 74, charw = 0.4428691387176514, offs = space;    \
    space -= 0.4428691387176514;
#define _K                                                       \
    if (space > 0.0 && space < 0.45614237785339357 && char == 0) \
        char = 75, charw = 0.45614237785339357, offs = space;    \
    space -= 0.45614237785339357;
#define _L                                                       \
    if (space > 0.0 && space < 0.40163798332214357 && char == 0) \
        char = 76, charw = 0.40163798332214357, offs = space;    \
    space -= 0.40163798332214357;
#define _M                                                      \
    if (space > 0.0 && space < 0.5874611854553222 && char == 0) \
        char = 77, charw = 0.5874611854553222, offs = space;    \
    space -= 0.5874611854553222;
#define _N                                                      \
    if (space > 0.0 && space < 0.4439987659454346 && char == 0) \
        char = 78, charw = 0.4439987659454346, offs = space;    \
    space -= 0.4439987659454346;
#define _O                                                      \
    if (space > 0.0 && space < 0.4451283931732178 && char == 0) \
        char = 79, charw = 0.4451283931732178, offs = space;    \
    space -= 0.4451283931732178;
#define _P                                                      \
    if (space > 0.0 && space < 0.4417397499084473 && char == 0) \
        char = 80, charw = 0.4417397499084473, offs = space;    \
    space -= 0.4417397499084473;
#define _Q                                                      \
    if (space > 0.0 && space < 0.4600960731506348 && char == 0) \
        char = 81, charw = 0.4600960731506348, offs = space;    \
    space -= 0.4600960731506348;
#define _R                                                      \
    if (space > 0.0 && space < 0.4428693771362305 && char == 0) \
        char = 82, charw = 0.4428693771362305, offs = space;    \
    space -= 0.4428693771362305;
#define _S                                                      \
    if (space > 0.0 && space < 0.4439990043640137 && char == 0) \
        char = 83, charw = 0.4439990043640137, offs = space;    \
    space -= 0.4439990043640137;
#define _T                                                      \
    if (space > 0.0 && space < 0.4174525260925293 && char == 0) \
        char = 84, charw = 0.4174525260925293, offs = space;    \
    space -= 0.4174525260925293;
#define _U                                                      \
    if (space > 0.0 && space < 0.4417397499084473 && char == 0) \
        char = 85, charw = 0.4417397499084473, offs = space;    \
    space -= 0.4417397499084473;
#define _V                                                      \
    if (space > 0.0 && space < 0.4581191062927246 && char == 0) \
        char = 86, charw = 0.4581191062927246, offs = space;    \
    space -= 0.4581191062927246;
#define _W                                                      \
    if (space > 0.0 && space < 0.5964982032775878 && char == 0) \
        char = 87, charw = 0.5964982032775878, offs = space;    \
    space -= 0.5964982032775878;
#define _X                                                       \
    if (space > 0.0 && space < 0.46489686965942384 && char == 0) \
        char = 88, charw = 0.46489686965942384, offs = space;    \
    space -= 0.46489686965942384;
#define _Y                                                       \
    if (space > 0.0 && space < 0.45218868255615235 && char == 0) \
        char = 89, charw = 0.45218868255615235, offs = space;    \
    space -= 0.45218868255615235;
#define _Z                                                      \
    if (space > 0.0 && space < 0.4185821533203125 && char == 0) \
        char = 90, charw = 0.4185821533203125, offs = space;    \
    space -= 0.4185821533203125;
#define _SP                                       \
    if (space > 0.0 && space < 0.25 && char == 0) \
        char = 32, charw = 0.25, offs = space;    \
    space -= 0.25;

//LINEDEFS

const vec3 COL_TEXT = vec3(0.1, 0.1, 0.1);
const vec3 COL_GROUND = vec3(0.36, 0.23, 0.16);
const vec3 CAM_POS = vec3(-2, 0.2, -1.8);
const float FOV_MUL = 0.8;
const int ReflSamples = 1;

const float gridSize = 16.0;
const float charWidthMul = 4.0;
const float textDepth = 2.0;

float getChar(vec2 uv, int ch) {
    uv.x /= gridSize;
    uv.y = clamp(uv.y, 0.0, 1.0 / gridSize);

    int xOffset = ch % int(gridSize);
    int yOffset = ch / int(gridSize);

    vec2 offset =
        vec2(float(xOffset) / gridSize, -float(yOffset + 1) / gridSize);
    vec2 pos = uv + offset;
    pos.y = 1.0 - pos.y;
    return texture(iChannel0, pos).r - 0.01;
}

float textTexture(vec2 uv, int defText) {
    if (uv.x < 0.0)
        return 0.9;

    uv.x *= charWidthMul;

    int char = 0;
    float offs = 0.0;
    float charw = 0.0;
    float space = uv.x;

    if (defText == 0) {
        LINE0
    }
    if (defText == 1) {
        LINE1
    }
    if (defText == 2) {
        LINE2
    }
    if (defText == 3) {
        LINE3
    }

    offs = uv.x - offs;

    vec2 uv2 = vec2(mod(uv.x - offs, charw) + (0.5 - charw / 2.0), uv.y * 0.4);

    float v = (char == 32 || char == 0) ? 0.9 : getChar(uv2, char);

    return v;
}

float measureLine(int defText) {
    int char = 1;
    float offs = 0.0;
    float charw = 0.0;
    float space = 0.;
    if (defText == 0) {
        LINE0
    }
    if (defText == 1) {
        LINE1
    }
    if (defText == 2) {
        LINE2
    }
    if (defText == 3) {
        LINE3
    }

    return -space / charWidthMul;
}

float extrudeDist(float d, float w, float y) {
    return length(vec2(max(d, 0.), y - clamp(y, -w, w))) +
           min(max(d, abs(y) - w), 0.);
}

float map_text(vec3 pos, vec3 offset, int defText, vec2 scale) {
    vec2 uv = (pos.xy / scale) + offset.xy;

    float text = (textTexture(uv, defText) - .5) * 0.3;
    text = extrudeDist(text, textDepth, pos.z + offset.z - textDepth);

    return text;
}

vec2 opU(vec2 d1, vec2 d2) {
    return (d1.x < d2.x) ? d1 : d2;
}

vec2 rotateVec(vec2 vect, float angle) {
    vec2 rv;
    rv.x = vect.x * cos(angle) + vect.y * sin(angle);
    rv.y = -vect.x * sin(angle) + vect.y * cos(angle);
    return rv;
}

vec3 map(vec3 pos) {
    vec3 posr = pos;

    vec2 res = vec2(1000.0, 0);
    float y = 0.0;
    for (int line = 5; line >= 0; --line) {
        float lineWidth = measureLine(line);
        if (lineWidth < 0.01)
            continue;

        float sizeMultiplier = charWidthMul / lineWidth;
        float lineHeight = 1.3;
        float d =
            map_text(posr,
                     vec3(lineWidth / 2.0,
                          y / 12. + lineHeight / 2. / 22., -0.),
                     line, vec2(sizeMultiplier, 16.0));
        y -= lineHeight;

        res = opU(res, vec2(d, 0));
    }

    float ground = pos.y + 0.1;
    res = opU(res, vec2(ground, 2));

    return vec3(res, y);
}

vec2 trace(vec3 cam, vec3 ray, float maxdist) {
    float t = 0.1;
    float objnr = 0.;
    vec3 pos;
    float dist;

    for (int i = 0; i < 200; ++i) {
        pos = ray * t + cam;
        vec2 res = map(pos).xy;
        dist = res.x;
        if (dist > maxdist || abs(dist) < 0.001)
            break;
        t += dist * 1.0;
        objnr = abs(res.y);
    }
    return vec2(t, objnr);
}

vec3 getNormal(vec3 pos, float e_f) {
    float dist = map(pos).x;
    vec2 e = vec2(e_f, 0.);
    vec3 n =
        dist - vec3(map(pos - e.xyy).x, map(pos - e.yxy).x, map(pos - e.yyx).x);
    return normalize(n);
}

float hardShadow(vec3 ro, vec3 rd) {
    float res = 1.0, t = 0.02;
    for (int i = 0; i < 24; i++) {
        vec2 hm = map(ro + rd * t).xy;
        float h = hm.x;
        res = min(res, 16.0 * h / t);
        if (h < 0.0004)
            return 0.0;
        t += clamp(h, 0.02, 0.25);
    }
    return clamp(res, 0.0, 1.0);
}

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = (fragCoord.xy - 0.5 * iResolution.xy) / iResolution.y;

    float lineHeight = map(vec3(0.)).z;

    // camera
    vec3 ro = CAM_POS;// +
              //vec3(cos(iTime + 1.570), sin(iTime + 4.7095) + 1.0, -sin(-iTime));
    vec3 ta = vec3(-0.0, -lineHeight / 1.3, 1.2);


    float fovExtra = 1.0;
    if(measureLine(2) > 0.01) { fovExtra = 1.2; ta.y -= 0.6; }
    if(measureLine(3) > 0.01) { fovExtra = 1.4; ta.y -= 1.0; }

    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(vec3(0, 1, 0), ww));
    vec3 vv = cross(ww, uu);

    vec3 rd = normalize(uv.x * uu + uv.y * vv + 0.5 / FOV_MUL / fovExtra * ww);

    // march
    vec3 col = vec3(0);
    vec3 touch = vec3(0);
    int m_p = 0;
    vec3 rrd = vec3(0);
    for (int i = 0; i < 1 + ReflSamples; i++) {
        const float maxdist = 30.;
        vec2 t_v = trace(ro, rd, maxdist);
        float t = t_v.x;
        int m = int(t_v.y);

        if (t < maxdist * 0.4) {
            vec3 pos = ro + rd * t;
            vec3 nor = getNormal(pos, 0.01);

            if (m == 0) {
                vec3 albedo = vec3(1.0, 1.0, 1.0);
                float metallic = 0.4;
                float roughness = 0.5;
                float ao = 0.0;
                vec3 F0 = mix(vec3(0.04), albedo, metallic);
                vec3 Lo = vec3(0.0);

                // Two directional lights
                for (int ic = 0; ic < 2; ++ic) {
                    vec3 L = (ic == 0) ? normalize(vec3(-0.2, 0.9, -1.0))
                                       : normalize(vec3(0.9, 0.2, -1.0));
                    vec3 H = normalize(L - rd);
                    vec3 lightColor =
                        (ic == 0) ? vec3(1.6, 1.45, 1.25) : vec3(0.6, 0.7, 1.2);

                    float NdotL = max(dot(nor, L), 0.0);
                    float NdotV = max(dot(nor, -rd), 0.0);
                    float NdotH = max(dot(nor, H), 0.0);
                    float VdotH = max(dot(-rd, H), 0.0);

                    float alpha = roughness * roughness;
                    float alpha2 = alpha * alpha;

                    float denom = (NdotH * NdotH) * (alpha2 - 1.0) + 1.0;
                    float D = alpha2 / (3.1415 * denom * denom + 1e-5);

                    float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
                    float G1 = NdotV / (NdotV * (1.0 - k) + k);
                    float G2 = NdotL / (NdotL * (1.0 - k) + k);
                    float G = G1 * G2;

                    vec3 F = F0 + (1.0 - F0) * pow(1.0 - VdotH, 5.0);

                    vec3 nominator = D * G * F;
                    float denomBRDF = 4.0 * NdotL * NdotV + 1e-5;
                    vec3 specular = nominator / denomBRDF;

                    vec3 kS = F;
                    vec3 kD = vec3(1.0) - kS;
                    kD *= 1.0 - metallic;

                    vec3 light =
                        lightColor * NdotL * hardShadow(pos + nor * 0.02, L);
                    Lo += (kD * albedo / 3.1415 + specular) * light;
                }

                vec3 acol = Lo;

                if (i == 0) {
                    col += acol;
                } else {
                    if (m_p == 2) {
                        col += 0.7 * COL_GROUND * acol /
                               max(1.0, pow(length(pos - touch), 2.0)) /
                               float(ReflSamples);
                    } else {
                        col += acol / max(1.0, pow(length(pos - touch), 2.0)) /
                               float(ReflSamples);
                    }
                }

                if (i == 0) {
                    ro = pos + nor * 0.01;
                    touch = ro;
                    rrd = reflect(rd, nor);
                    m_p = m;
                }

                rd =
                    rrd +
                    0.2 *
                        vec3(
                            rand(uv + vec2(0.0 + sin(iTime) + float(i) / 10.0)),
                            rand(uv + vec2(0.3 + cos(iTime) + float(i) / 10.0)),
                            rand(uv +
                                 vec2(0.6 + sin(iTime) + float(i) / 10.0)));
                rd = normalize(rd);

                // if(i == 0) break;
            } else {
                vec3 Lo = vec3(0.);
                for (int ic = 0; ic < 2; ++ic) {
                    vec3 L = (ic == 0) ? normalize(vec3(0.9, 0.9, -1.0))
                                       : normalize(vec3(0.2, 0.2, -1.0));
                    vec3 H = normalize(L - rd);
                    vec3 lightColor =
                        (ic == 0) ? vec3(1.6, 1.45, 1.25) : vec3(0.6, 0.7, 1.2);

                    float sha = hardShadow(pos + nor * 0.001, L);
                    float NdotL = max(dot(nor, L), 0.0);

                    Lo += NdotL * sha * lightColor;
                }

                if (m_p != 2)
                    col += COL_GROUND / 2.0 * Lo /
                           ((i == 0) ? 1.0 : float(ReflSamples));

                if (i == 0) {
                    ro = pos + nor * 0.01;
                    touch = ro;
                    rrd = reflect(rd, nor);
                    m_p = m;
                }

                rd =
                    rrd +
                    0.2 *
                        vec3(
                            rand(uv + vec2(0.0 + sin(iTime) + float(i) / 10.0)),
                            rand(uv + vec2(0.3 + cos(iTime) + float(i) / 10.0)),
                            rand(uv +
                                 vec2(0.6 + sin(iTime) + float(i) / 10.0)));
                rd = normalize(rd);
            }
        }
    }

    col = pow(col, vec3(1. / 2.2));
    fragColor = vec4(col, 1.0);
}
""".trimIndent()

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val sanitizedString = text.uppercase().filter {
            (it >= 'A' && it <= 'Z') || it == ' ' || it == '\n'
        }

        val minLines = listOf("", "", "", "")

        val lineDefs = (sanitizedString.split("\n") + minLines).mapIndexed { i, v ->
            "#define LINE${i} " + v.map { when {
                it == ' ' -> "_SP"
                else -> "_$it"
            } }.joinToString(separator = " ")
        }.joinToString(separator = "\n")

        var source = shaderToyShader
        source = source.replace("//LINEDEFS", lineDefs)

        val bitmap = try {
            renderShaderToBitmap(
                context,
                source,
                width = 450,
                height = 450,
                iChannels = listOf(loadBitmapFromAssets(context, "fonts/noto-mono-alphabet.png"))
            )
        }catch(e: Exception) {
            return null
        }

        return LineRenderResult(
            bitmap = bitmap,
            lineHeight = 0,
            startXOffset = 0,
            startYOffset = 0
        )
    }

    override fun renderMultiLine(context: Context, text: String): Bitmap? =
        renderLine(context, text)?.bitmap
}