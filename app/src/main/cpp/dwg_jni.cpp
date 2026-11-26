#include <jni.h>
#include <string>
#include <sstream>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "DwgBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef HAVE_LIBREDWG
extern "C" {
#include <dwg.h>
#include <dwg_api.h>
}
#endif

static std::string escapeJson(const std::string& input) {
    std::ostringstream oss;
    for (unsigned char c : input) {
        switch (c) {
            case '"': oss << "\\\""; break;
            case '\\': oss << "\\\\"; break;
            case '\n': oss << "\\n"; break;
            case '\r': oss << "\\r"; break;
            case '\t': oss << "\\t"; break;
            default:
                if (c >= 0x20) oss << c;
                break;
        }
    }
    return oss.str();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cadviewer_dwg_cad_DwgNativeBridge_isLibreDwgLinked(JNIEnv*, jobject) {
#ifdef HAVE_LIBREDWG
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cadviewer_dwg_cad_DwgNativeBridge_parseDwgFileToJson(
        JNIEnv* env,
        jobject,
        jstring jFilePath) {
    const char* cPath = env->GetStringUTFChars(jFilePath, nullptr);
    std::string filePath(cPath ? cPath : "");
    env->ReleaseStringUTFChars(jFilePath, cPath);

#ifdef HAVE_LIBREDWG
    Dwg_Data dwg;
    memset(&dwg, 0, sizeof(Dwg_Data));
    int error = dwg_read_file(filePath.c_str(), &dwg);
    if (error >= DWG_ERR_CRITICAL) {
        LOGE("LibreDWG failed to read file %s (err=%d)", filePath.c_str(), error);
        dwg_free(&dwg);
        return env->NewStringUTF("{\"ok\":false,\"error\":\"Khong the giai ma tep DWG bang LibreDWG\"}");
    }

    std::ostringstream json;
    json << "{\"ok\":true,\"entities\":[";
    bool first = true;

    for (BITCODE_BL i = 0; i < dwg.num_objects; i++) {
        Dwg_Object* obj = &dwg.object[i];
        if (!obj || obj->supertype != DWG_SUPERTYPE_ENTITY) continue;

        const char* layerName = "0";
        Dwg_Object_Entity* ent = obj->tio.entity;
        if (ent && ent->layer && ent->layer->obj && ent->layer->obj->tio.object) {
            Dwg_Object_LAYER* layer = ent->layer->obj->tio.object->tio.LAYER;
            if (layer && layer->name) {
                layerName = layer->name;
            }
        }

        if (obj->fixedtype == DWG_TYPE_LINE) {
            Dwg_Entity_LINE* line = ent->tio.LINE;
            if (!first) json << ",";
            first = false;
            json << "{\"type\":\"LINE\",\"layer\":\"" << escapeJson(layerName)
                 << "\",\"x1\":" << line->start.x << ",\"y1\":" << line->start.y
                 << ",\"x2\":" << line->end.x << ",\"y2\":" << line->end.y << "}";
        } else if (obj->fixedtype == DWG_TYPE_CIRCLE) {
            Dwg_Entity_CIRCLE* circle = ent->tio.CIRCLE;
            if (!first) json << ",";
            first = false;
            json << "{\"type\":\"CIRCLE\",\"layer\":\"" << escapeJson(layerName)
                 << "\",\"cx\":" << circle->center.x << ",\"cy\":" << circle->center.y
                 << ",\"r\":" << circle->radius << "}";
        } else if (obj->fixedtype == DWG_TYPE_ARC) {
            Dwg_Entity_ARC* arc = ent->tio.ARC;
            if (!first) json << ",";
            first = false;
            json << "{\"type\":\"ARC\",\"layer\":\"" << escapeJson(layerName)
                 << "\",\"cx\":" << arc->center.x << ",\"cy\":" << arc->center.y
                 << ",\"r\":" << arc->radius
                 << ",\"startAngle\":" << (arc->start_angle * 180.0 / M_PI)
                 << ",\"endAngle\":" << (arc->end_angle * 180.0 / M_PI) << "}";
        } else if (obj->fixedtype == DWG_TYPE_LWPOLYLINE) {
            Dwg_Entity_LWPOLYLINE* pl = ent->tio.LWPOLYLINE;
            if (pl->num_points >= 2) {
                if (!first) json << ",";
                first = false;
                bool closed = (pl->flag & 512) != 0 || (pl->flag & 1) != 0;
                json << "{\"type\":\"LWPOLYLINE\",\"layer\":\"" << escapeJson(layerName)
                     << "\",\"closed\":" << (closed ? "true" : "false") << ",\"points\":[";
                for (BITCODE_BL p = 0; p < pl->num_points; p++) {
                    if (p > 0) json << ",";
                    json << "[" << pl->points[p].x << "," << pl->points[p].y << "]";
                }
                json << "]}";
            }
        } else if (obj->fixedtype == DWG_TYPE_INSERT) {
            Dwg_Entity_INSERT* ins = ent->tio.INSERT;
            const char* blockName = "BLOCK";
            if (ins->block_header && ins->block_header->obj && ins->block_header->obj->tio.object) {
                Dwg_Object_BLOCK_HEADER* bh = ins->block_header->obj->tio.object->tio.BLOCK_HEADER;
                if (bh && bh->name) blockName = bh->name;
            }
            if (!first) json << ",";
            first = false;
            json << "{\"type\":\"INSERT\",\"layer\":\"" << escapeJson(layerName)
                 << "\",\"name\":\"" << escapeJson(blockName)
                 << "\",\"x\":" << ins->ins_pt.x << ",\"y\":" << ins->ins_pt.y << "}";
        }
    }

    json << "]}";
    dwg_free(&dwg);
    return env->NewStringUTF(json.str().c_str());
#else
    LOGI("LibreDWG not linked; returning fallback notice for %s", filePath.c_str());
    return env->NewStringUTF("{\"ok\":false,\"needsNativeLib\":true,\"error\":\"Chua nhung thu vien libredwg.a\"}");
#endif
}
