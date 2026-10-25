// Explicit isolated fixture: real CUser construction and real random-box SQL.
// No clients, external addresses, result replacement or production hook changes.
(function exerciseNativeRandomBox() {
    'use strict';
    var done = false;
    Interceptor.attach(ptr('0x086c4134'), {
        onLeave: function (result) {
            if (done || result.toInt32() === 0) return;
            done = true;
            var module = Process.getModuleByName('libdof-plugin.so');
            function native(address, ret, args) {
                return new NativeFunction(address, ret, args, {abi:'sysv',exceptions:'propagate'});
            }
            var manager = native(module.getExportByName('_ZN9SingletonI5DBMgrE11GetInstanceEv'), 'pointer', [])();
            var database = native(module.base.add(0x491f0),'pointer',['pointer'])(manager);
            var setQuery = native(ptr('0x083f41c0'),'int',['pointer','pointer']);
            var execute = native(ptr('0x083f4326'),'int',['pointer','int']);
            var rows = native(ptr('0x080e236c'),'int',['pointer']);
            function sql(text) {
                setQuery(database,Memory.allocUtf8String(text));
                if (execute(database,1) !== 1) throw new Error('Fixture SQL execution failed');
            }
            // Calls made inside another Interceptor callback suppress nested
            // listener callbacks in this Gadget. Read the real native result
            // directly; the independent MySQL general log verifies execution.
            var queries=[], counts=[];
            var user=Memory.alloc(2*1024*1024),constructed=false;
            try {
                sql('SELECT item_id FROM dof_native.randombox');
                if(rows(database)!==0) throw new Error('Fixture requires an empty random-box table');
                // Duplicate item ids establish DISTINCT behavior, not just query spelling.
                sql('INSERT INTO dof_native.randombox(item_id,item_num,Probability,Announcement,Grouping,rarity) VALUES(1001,1,1,0,1,0),(1001,1,1,0,1,0),(1002,1,1,0,1,0)');
                native(ptr('0x0864e410'),'void',['pointer'])(user);constructed=true;
                var randomBox=native(module.getExportByName('_ZN5DBMgr12DB_randomboxEP5CUserii'),'void',['pointer','pointer','int','int']);
                randomBox(manager,user,5,1);
                var randomDatabase=manager.add(0x40).readPointer();
                if(randomDatabase.isNull()) throw new Error('Random-box connection is absent');
                // Pinned game MySQL::set_query stores its query buffer at +0x1c.
                var executed=randomDatabase.add(0x1c).readUtf8String();
                if(executed==='SELECT DISTINCT item_id FROM dof_native.randombox') queries.push(executed);
                counts.push(rows(randomDatabase));
                sql('SELECT item_id FROM dof_native.randombox');
                if(rows(database)!==3) throw new Error('Read-only listing changed fixture rows');
                console.log('[Dof random-box feature] '+JSON.stringify({
                    returned:true,queries:queries,rowCounts:counts,sourceRows:3,distinctRows:2,
                    observedDistinct:counts.indexOf(2)>=0,
                    userConstructed:constructed,playerLoggedIn:false,inventoryRewardsVerified:false
                }));
            } finally {
                if(constructed) native(ptr('0x0864ef9c'),'void',['pointer'])(user);
                sql('DELETE FROM dof_native.randombox');
            }
        }
    });
})();
