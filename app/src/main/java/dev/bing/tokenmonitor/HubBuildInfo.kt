package dev.bing.tokenmonitor

import kotlinx.serialization.json.*

/** Public build metadata only. Never treats health.version as an application version. */
data class HubBuildInfo(val runtime:String, val schemaVersion:Long?=null,
    val coreRevision:Long?=null, val coreBuildId:String?=null,
    val runtimeRevision:Long?=null, val runtimeBuildId:String?=null, val buildPresent:Boolean=true) {
    val status:String get() {
        if(!buildPresent) return "legacy"
        if(runtime !in HubBuildRegistry.entries) return "unknown"
        val schema=schemaVersion ?: return "unknown"
        if(schema>1) return "remoteNewer"
        if(schema<1) return "updateAvailable"
        val core=coreRevision ?: return "unknown";val adapter=runtimeRevision ?: return "unknown"
        val expectedCore=HubBuildRegistry.entries.getValue("core").keys.max()
        val expectedRuntime=HubBuildRegistry.entries.getValue(runtime).keys.max()
        fun matches(component:String,revision:Long,id:String?,expected:Long):Boolean {
            if(id==null) return false
            return HubBuildRegistry.entries.getValue(component)[revision]?.let {it==id} ?: (revision>expected)
        }
        if(!matches("core",core,coreBuildId,expectedCore) || !matches(runtime,adapter,runtimeBuildId,expectedRuntime)) return "unknown"
        val older=core<expectedCore || adapter<expectedRuntime
        val newer=core>expectedCore || adapter>expectedRuntime
        return when {older && newer->"unknown";older->"updateAvailable";newer->"remoteNewer";else->"current"}
    }
    internal fun cache():String=buildJsonObject {
        put("runtime",runtime)
        if(buildPresent) put("hubBuild",buildJsonObject {
            put("runtime",runtime)
            schemaVersion?.let {put("schemaVersion",it)}
            coreRevision?.let {put("coreRevision",it)};coreBuildId?.let {put("coreBuildId",it)}
            runtimeRevision?.let {put("runtimeRevision",it)};runtimeBuildId?.let {put("runtimeBuildId",it)}
        })
    }.toString()
    companion object {
        internal fun parse(body:String):HubBuildInfo?=runCatching {
            val root=Json.parseToJsonElement(body) as? JsonObject ?: return null
            val build=root["hubBuild"] as? JsonObject
            fun text(obj:JsonObject?,key:String)=(obj?.get(key) as? JsonPrimitive)?.takeIf {it.isString}?.content
            fun revision(key:String)=(build?.get(key) as? JsonPrimitive)?.longOrNull?.takeIf {it>0}
            fun fingerprint(key:String)=text(build,key)?.takeIf {Regex("sha256:[a-f0-9]{64}").matches(it)}
            val runtime=when((text(build,"runtime") ?: text(root,"runtime")).orEmpty().trim().lowercase()) {
                "node","hub","node-hub"->"node-hub";"worker","cloudflare-worker"->"cloudflare-worker";else->""
            }
            HubBuildInfo(runtime,revision("schemaVersion"),revision("coreRevision"),fingerprint("coreBuildId"),revision("runtimeRevision"),fingerprint("runtimeBuildId"),root.containsKey("hubBuild"))
        }.getOrNull()
    }
}

/** Exact build registry from desktop v0.76.0 (fbbe4c1), under its MIT license. */
internal object HubBuildRegistry {
    val entries=mapOf(
        "core" to mapOf(
            1L to "sha256:471ef0a506d39a43e6bff54dac28c8b81aa63a243fa8879dbfdf1756f903af53",
            2L to "sha256:26bf4a6d7716780727f76bee1b56d423c7429a1bc7cb45bff626c1dbcb1f62ce",
            3L to "sha256:ef3b4bc1b1f69413e4ed12090aae4a638826bf7b064b7e97ae0e0b89125708dd",
            4L to "sha256:6ca66e4e1047c7116064764cb69ab9ccd8f7ea3673d863d16903bea8a0dc71aa",
            5L to "sha256:eeb12917cb868d0564e8fc159bfa9c05af3c88bc2a5f3117f948f14f3a155205",
            6L to "sha256:006ab65f658958e8f11ac8b5646e86531ce8d142321dccbe92db8edbc727ae94",
            7L to "sha256:b08b43274dd4a3086e99e37f6166cf40004885adf1b5733d5dbec245d1cbce85",
            8L to "sha256:55eb741803fe0e91bdcc92242de8cc0541ed5ce65dffbd660c34c1f7da3faa2d",
            9L to "sha256:201b15614a47a403838c769152081f5e888a7e32f6f11c9c5170212323614ef7",
            10L to "sha256:e78582667a295c3c9e16d35b67d4188a5d559a61fec739048460d95af85340ae",
            11L to "sha256:798c308d20d7f9a22aad74f6feeb5f168210fd465c6aee590b517c40e801c292",
            12L to "sha256:cea5b085ec636630ce14ca146f7ebf0b5addbc91dfabc8fe353bafe1b3eea59e",
            13L to "sha256:31183487e544b1c6e108c5f34ccc2c6c1f55cdbfc5766100e0ed0ba119db2997",
            14L to "sha256:668b2cdcec325133b9252aff01a1f993f516dd4d73919e3f68d816a9277b688e",
            15L to "sha256:8de681efdcf4333642073f693f4a604073af25d8ebfb21bc942375c567818b61",
            16L to "sha256:f7c6be336aa68e46f09c275fb2f5cc2cf146f4dbba472b6746da57b9a89e1bbf",
            17L to "sha256:5f0fc24d007a9e8feb1e6741d09efb77a4bd3f6da685e8a56f01480567876872",
            18L to "sha256:4074b6e85c0cb32e3d8978fbdcfcbcba03a2c1e0b3d95bbc20177e141004a93e",
            19L to "sha256:b37d2c98aa97998a8eb8f9b0fbf5d9bee649df21d8c992a6a550d20246fe8ed8",
            20L to "sha256:aaef4e15753be2a38b46afeb9b965b640e82d0377f77259830edd16ef146a4ae",
            21L to "sha256:c9ec6275aef9c71b71bab67d603ddd86800d3c79e5d14c2d23990b0564b3b009",
            22L to "sha256:e86d0e3ed65f1dfa4997f891fb7588a5a8739d9cd01b5be634a9882cfb32754d",
            23L to "sha256:28bc211d7df29be878affba0d224f51d731e80d821c8e928c7a4eca394b69fae",
            24L to "sha256:14b2079b9bd8159cd6f0e352ed363cb69c42f85cf492a2845a122f8a67518dad",
            25L to "sha256:495337ae8a75f8b8adb673a17fbc09db2b8adeeed77f10de480f6bfc8bc31aa8",
            26L to "sha256:797870407454094ca1c9936376d36da7f5ab08272579042cb3905be11f78cf8d",
            27L to "sha256:652a99f9f63eaa3901a874e4287cd115f5c1ada6393fc3a19c106ea685386ff7",
            28L to "sha256:ff1bcb7b4b18e8478dba042482f3af3a8a0688853d64164403e411ace363ea58",
            29L to "sha256:25bd090ffc832bcda78079e9577bccd837e0cad585014209dc0c8a6176f1d94c",
            30L to "sha256:de736e6d49ae5dbf2a0d845ce54863e1700c2fdcf61dd5565ea3c4e2de37823f",
            31L to "sha256:65330451639ea14fcb5be7fac23c1e242cfeb0cf630d5a457210001ecf54dff3",
            32L to "sha256:98662ba11c23e6b8f13dfc65fc673a00c547fc3426799ee95c4448ed974afdb1",
            33L to "sha256:5d1dde649a4455029699fa67ad041f4ab2f2f47883410331df397a1ff3490369",
            34L to "sha256:38a92231c669fb3c3b62bd1fddb42e53b4183c5f88182114a0f0101b92e8cd84",
            35L to "sha256:361f0c57fa8cad7468f14b88b71e30afbfeef35e0e097c5721d4778c083f790b",
            36L to "sha256:e19784d3e95b07d617b10bec1c38eb54d6cc5984c395e0366351a78d96b89153",
            37L to "sha256:d8dd3600ac198ea2f882c8db124a2e9f1a2912a24ef74c02d7fe8f979303e27d",
            38L to "sha256:4a0491395a99e3c21f1e569b08bd0d095218064f5ab2cafd3c7acfc73c52cf59",
            39L to "sha256:c694f594e050e3fc1e9b43afa8030da21bd794ef5a3ad577a3be27d9c68e0d7b",
            40L to "sha256:ee7acff4b6d5b626d5fa9ff65956e05b5c2f2981215dc54fadab2fe6f65c6bd9",
            41L to "sha256:e329542f91db727294d5a14222c7a83aa06f4d2d77cf4f946856a7c177c7b03d",
            42L to "sha256:7131010a2a9ee260f43a33116b678af9d5e40eb58e00648135bd9f0866df62f1",
            43L to "sha256:940b9e51776afbe88ed385b99b7e13067b3293e5fd5d031d2315953e849b8c1f",
            44L to "sha256:f68b607d402fd2d765821f3efc636d18eec0ee079671a3a31da83ed79e712379",
            45L to "sha256:66c822dd5090fe182ec28792173ebc64f4bf93f6e2eeafb47ee17cfb3027569b",
            46L to "sha256:a1cb8a4c374a621d609c1c150f896e750c373b73c5e015f072a89aa351e43f52",
            47L to "sha256:744d8710934650eddb295c162f2dcf98b8669915a7cb6ff1324e285fa6b0e0bf",
            48L to "sha256:bf12a14a1df4ed227747f76d9434536f012e3283dfb984e3d53295ac9399d2d6",
            49L to "sha256:be6e92b5cb4760e461924e7033c90c3daea2fe273e1bc7de97be867de674682f",
            50L to "sha256:5e911f11ca27f30e58bff1a505b19df479e1850e31cd0de1bd216f7d1d45ff06",
            51L to "sha256:213e07e7a4306b4b116337992946257a051b48d5bad076b51809baeb64473a3e",
            52L to "sha256:2cd0eb22547f5d43bba65f6ec7f9d25c3374c0d0c6c06d26bd9a1dff7cc42f73",
            53L to "sha256:7c489b556115fa7a8f5827392246cac13b4a97b7abaa254ef8d4cb7fa21b18cc",
            54L to "sha256:ba091f284abfce80aaca4b4a697b79bb1f55bcdece81c0661b481e20ae8f5a73",
            55L to "sha256:eb8f2c24c60b4fcd71e526f0e23b1814a5723fa3e752443fcf4b1928671fe74a",
            56L to "sha256:4e05aa96e82eef72b2ee542cb0fed4f301b58ee8b6d9e4d07aabbf9f831ac246",
            57L to "sha256:3f6e323fcaa8c25ae850606c9cd8dc59a36175cda6bb47328e8aee6412733d51",
            58L to "sha256:7780f8e8124f9dd129f884b8ed0ef0e843d5b05ecb50dd34cc6083cab3fabe61",
            59L to "sha256:3894462eca8c8ce206bf5dc9fcf55c04f8f8ae0ab743afbca34edc46279e8b3a",
            60L to "sha256:fb62c3e881456be507ec17367f30ed23d29e82428e52c7afc9d289e09b56287e",
            61L to "sha256:06c4db1ec5beb7c072424b67c0d3519c3553d411749318babdc493df93a18598",
            62L to "sha256:1b3ee89b5f6bedfe3b6ef4ebf07941daca1e67e6ca2fd3007bb17e021f344416",
            63L to "sha256:3f5632be37b5ea5e79aa4703cf207de879a9fc455fc23c14aec22aff07a564ff",
            64L to "sha256:10252c17e59ce9f56628cd91902895895d75a2e11f08b4df88ad056d21ec956c",
            65L to "sha256:1deabed0cf8655c45eb8839ed657ff1f982c6cd4b1e2f5e5c9891e2f9eb2c0f0",
            66L to "sha256:76b26396d9b1c3e1534b4832548085e56dd2f144f5e87ac1aa334ecdc1b43624",
            67L to "sha256:0123b02da54b8f55bd40cca80a66393a02dc9a26124f9da39bddbb33a507ef93",
            68L to "sha256:dc48876d57784ea0d6e7908bda050847558c167e785343aca4ea129eb520d676",
            69L to "sha256:52b0c37e5f6ae7571441b85b06c97a2d2f998c04bfc101eb3b7e8b57a870f0d6",
            70L to "sha256:7eeda67d5ac35547174ebb9d8dc608f8687852f65063ad71ddf9f94a3b0dda3f",
            71L to "sha256:d4d518133984cf0eff80e3d91e15ef84b14715378978e646be90f2cfdd2657a0",
            72L to "sha256:def1356528aba303a32dd706822a1cf566a2e45a39407bf1aa54cce690f14958",
            73L to "sha256:8a286284faa3d9eda68ec806afada17710c557cf87ae45952022f21e1a063a84",
            74L to "sha256:c9795797703270ec36431d4dcccbf46b2ce8529bddd1cd8c21c31bf9d861ae7a",
            75L to "sha256:6808386d1f1dd37caaf1640cbe41c3b9be803c8dfd5952bb3dbb009a6756e345",
            76L to "sha256:c5bb4e95d7f1c593113b32f21d4c158ccfd3a7992727194990a72ec31b9f2523",
            77L to "sha256:87359e4f614349c0e166e145b49c165f233cfb285148205a61cb6bdd23b93780",
            78L to "sha256:4e3d46d08d77cf74e417e0440a58c221caaf201f638f9ea0415cbac868bbfd4c",
            79L to "sha256:bfcc10d125a8eff0f642c4daaea6bd5192920ce74d427025cdf534f8deb5e36d",
            80L to "sha256:04f734afadd03540a3db992734808c07803460c4bf4296d912dbf2816102a32a",
            81L to "sha256:1bdf526dd665a840e2ed280103aa16511ed7b6fec06655a25026af8c88fcf9ba",
            82L to "sha256:139183e108139b528f8ed24ce079a74fa317a4b156dd6debf9a1968b1fc37d82",
            83L to "sha256:597ec1ee025bdf3bca0ca1509d60a6b3317c138f0573c06e2a6eecbb86206c9b",
            84L to "sha256:2b9f3aca4c51b909d494f73729a2cbe3d47df5ba5de251a59dcf5f209b3a18e4",
            85L to "sha256:70ba30f37087c2036c542749bfa4edc4f6e5be8eff622e0afbfba912af8974e4",
            86L to "sha256:834e4e2abcc39f7f5ef452601e94092c95efcb87ae10219438c93e5c430d1ca1",
            87L to "sha256:dc51c5d0515e2309792e099db870837030ca9004f5fffd19a55a376e9a53827a",
            88L to "sha256:43e2cae75b5d4023a92e01ad2a686cf7fcece84793984a779f4e0802300f7744",
            89L to "sha256:4a8ae97c67684ce8c75f275e3823ebabf1a4d00ac5b8c20718254fb92e30b4a8"
        ),
        "node-hub" to mapOf(
            1L to "sha256:dbdaa0b2aa2e8d627b939d6ab76a9029aa2807839fdbe4e7918edcab592fe749",
            2L to "sha256:8c938cc1f7f0717964c84e85f2620b88620115d8e539b362dddf20097b66fe45",
            3L to "sha256:3bbeb3606151b55254ccfe4d847b050b328c587fa94e165f4258a1a7f14fdc79",
            4L to "sha256:f1db81c0d1ede372aaf74c39514e00d19bee54f064dec77e5554c6434eb6ceb0",
            5L to "sha256:d825a515df8f90735f15682eaae40d039b266b21a0e282667d6f6472987977e8"
        ),
        "cloudflare-worker" to mapOf(
            1L to "sha256:a02f96b20ea72eb22ce4ee81f738023661742830f705c1f89fb71259ec41fd72",
            2L to "sha256:e3452d9ea31bd30af6f9318d56cf258628897f90bc3af75291dc53631cf69509",
            3L to "sha256:7b98c4a6209c258ceac7ecc781cf6b9c0f9e803b0b5716a533fa1dafec954ad9",
            4L to "sha256:9e3977bad4a1399e8d0d959db5f88180d8d106f717f68e527cc7dd843272fb46",
            5L to "sha256:63d5baedb008de3d80f4da376c8579fb7e7d906fda51e7f5264430adf0597df5",
            6L to "sha256:55a2b202dd01754eb1e8f59fecdfab3bc5fd14e1bbd71ea63f00e36dfb127c81"
        )
    )
}
