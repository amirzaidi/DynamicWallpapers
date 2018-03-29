#pragma version(1)
#pragma rs java_package_name(amirz.dynamicwallpapers)

const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

float contrastIncrease = 1.0f;
float brightnessOffset = 0.0f;

float saturationIncrease = 1.0f;

void setContrast(float contrast) {
    contrastIncrease = contrast;
    brightnessOffset  = 127.0f - contrastIncrease * 127.0f;
}

uchar4 __attribute__((kernel)) transform(uchar4 in, uint32_t x, uint32_t y) {
    uchar4 pixelOut;

    float4 f4 = rsUnpackColor8888(in);
    float3 result = dot(f4.rgb, gMonoMult);
    result = mix(result, f4.rgb, saturationIncrease);

    pixelOut = rsPackColorTo8888(result);
    pixelOut.r = clamp((int)(brightnessOffset + pixelOut.r * contrastIncrease), 0, 255);
    pixelOut.g = clamp((int)(brightnessOffset + pixelOut.g * contrastIncrease), 0, 255);
    pixelOut.b = clamp((int)(brightnessOffset + pixelOut.b * contrastIncrease), 0, 255);
    return pixelOut;
}